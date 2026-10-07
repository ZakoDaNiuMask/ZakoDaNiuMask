// SPDX-License-Identifier: GPL-3.0-or-later
//
// Minimal Model Context Protocol (MCP) types for the `ksud mcp` stdio server.
// Only the subset needed for tool exposure is implemented:
//   initialize / initialized / ping / tools/list / tools/call / shutdown
// plus notifications. See https://modelcontextprotocol.io for the spec.

use serde::{Deserialize, Serialize};
use serde_json::Value;

pub const PROTOCOL_VERSION: &str = "2025-06-18";
pub const SERVER_NAME: &str = "ksud-mcp";

#[derive(Debug, Deserialize)]
pub struct Request {
    /// Absent for notifications.
    #[serde(default)]
    pub id: Option<Value>,
    pub method: String,
    #[serde(default)]
    pub params: Option<Value>,
}

#[derive(Debug, Serialize)]
pub struct Response {
    pub jsonrpc: &'static str,
    pub id: Value,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub result: Option<Value>,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub error: Option<RpcError>,
}

impl Response {
    pub fn ok(id: Value, result: Value) -> Self {
        Response {
            jsonrpc: "2.0",
            id,
            result: Some(result),
            error: None,
        }
    }

    pub fn err(id: Value, code: i64, message: impl Into<String>) -> Self {
        Response {
            jsonrpc: "2.0",
            id,
            result: None,
            error: Some(RpcError {
                code,
                message: message.into(),
            }),
        }
    }
}

#[derive(Debug, Serialize)]
pub struct RpcError {
    pub code: i64,
    pub message: String,
}

/// A single content block in a `tools/call` result.
#[derive(Debug, Serialize)]
#[serde(tag = "type")]
pub enum Content {
    #[serde(rename = "text")]
    Text { text: String },
}

pub fn tool_result(text: String, is_error: bool) -> Value {
    serde_json::json!({
        "content": [ Content::Text { text } ],
        "isError": is_error,
    })
}

pub fn initialize_result() -> Value {
    serde_json::json!({
        "protocolVersion": PROTOCOL_VERSION,
        "capabilities": { "tools": { "listChanged": false } },
        "serverInfo": { "name": SERVER_NAME, "version": env!("CARGO_PKG_VERSION") },
        "instructions": "ksud MCP server. Tools expose KernelSU/ksud capabilities. \
    Read-only tools are always available; write and danger tools require raising \
    `max_tier` in /data/adb/ksu/.mcp_policy.json."
    })
}
