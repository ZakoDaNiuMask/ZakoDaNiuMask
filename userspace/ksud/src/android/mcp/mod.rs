// SPDX-License-Identifier: GPL-3.0-or-later
//
// `ksud mcp` - a Model Context Protocol server over stdio.
//
// Reads newline-delimited JSON-RPC 2.0 requests on stdin and writes responses
// on stdout. Intended to be driven by an MCP host, either on-device (the
// manager app spawns it via su) or on a desktop host over `adb shell ksud mcp`.
//
// Only these methods are implemented: initialize, notifications/initialized,
// ping, tools/list, tools/call, shutdown. Unknown requests get -32601.

pub mod policy;
pub mod protocol;
pub mod tools;

use std::io::{BufRead, Write};

use anyhow::Result;
use serde_json::{Map, Value, json};

use protocol::{Request, Response};

/// Run the stdio MCP server loop. Returns when stdin reaches EOF or a
/// `shutdown` request is received.
pub fn run() -> Result<()> {
    if let Err(e) = policy::ensure_default_file() {
        log::warn!("mcp: could not write default policy: {e}");
    }
    let policy = policy::Policy::load();
    log::info!(
        "mcp: server started (max_tier={})",
        policy.max_tier.as_str()
    );

    let stdin = std::io::stdin();
    let mut stdout = std::io::stdout();

    for line in stdin.lock().lines() {
        let line = match line {
            Ok(line) => line,
            Err(e) => {
                log::warn!("mcp: stdin read error: {e}");
                break;
            }
        };
        let trimmed = line.trim();
        if trimmed.is_empty() {
            continue;
        }

        let request: Request = match serde_json::from_str(trimmed) {
            Ok(request) => request,
            Err(e) => {
                log::warn!("mcp: invalid request: {e}");
                let resp = Response::err(Value::Null, -32700, format!("parse error: {e}"));
                write_response(&mut stdout, &resp)?;
                continue;
            }
        };

        let is_notification = request.id.is_none();
        match dispatch(&request, &policy) {
            Some(resp) => write_response(&mut stdout, &resp)?,
            None if is_notification => {}
            None => {
                let resp = Response::err(
                    request.id.clone().unwrap_or(Value::Null),
                    -32601,
                    format!("method not found: {}", request.method),
                );
                write_response(&mut stdout, &resp)?;
            }
        }

        if request.id.is_some() && request.method == "shutdown" {
            log::info!("mcp: shutdown requested");
            break;
        }
    }
    Ok(())
}

fn dispatch(request: &Request, policy: &policy::Policy) -> Option<Response> {
    let id = request.id.clone()?; // notifications (no id) are handled by the caller
    let params = request.params.clone().unwrap_or(Value::Null);

    let response = match request.method.as_str() {
        "initialize" => Response::ok(id, protocol::initialize_result()),
        "ping" => Response::ok(id, json!({})),
        "tools/list" => Response::ok(id, tools::descriptors()),
        "tools/call" => Response::ok(id, call_tool(policy, &params)),
        "shutdown" => Response::ok(id, json!({})),
        _ => Response::err(id, -32601, format!("method not found: {}", request.method)),
    };
    Some(response)
}

fn call_tool(policy: &policy::Policy, params: &Value) -> Value {
    let name = match params.get("name").and_then(Value::as_str) {
        Some(name) => name,
        None => return protocol::tool_result("missing tool name".into(), true),
    };
    let empty = Map::new();
    let args = params
        .get("arguments")
        .and_then(Value::as_object)
        .unwrap_or(&empty);

    let tool = match tools::find(name) {
        Some(tool) => tool,
        None => return protocol::tool_result(format!("unknown tool: {name}"), true),
    };

    if !policy.permits(name, tool.tier) {
        return protocol::tool_result(policy.denial_reason(name, tool.tier), true);
    }

    match tools::execute(tool, args) {
        Ok((text, is_error)) => protocol::tool_result(text, is_error),
        Err(e) => protocol::tool_result(format!("tool '{name}' failed: {e:#}"), true),
    }
}

fn write_response(stdout: &mut std::io::Stdout, response: &Response) -> Result<()> {
    let line = serde_json::to_string(response)?;
    stdout.write_all(line.as_bytes())?;
    stdout.write_all(b"\n")?;
    stdout.flush()?;
    Ok(())
}
