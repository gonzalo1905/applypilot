# Using ApplyPilot from Claude (MCP)

ApplyPilot is also an [MCP](https://modelcontextprotocol.io) server. An MCP client such as
Claude Code or Claude Desktop can read your board and your match results, so you can ask
things like *"which of my applications match my CV best?"* or *"draft a follow-up email for
the Northwind interview using my match result"*.

## What it offers

| Tool | What it returns |
|---|---|
| `list_applications(status?)` | Your applications: id, company, role, column, date applied, location |
| `get_match(applicationId)` | The latest match of your CV with one application: score, matched skills with CV quotes, missing skills, cover letter, reviewer verdict |

It is **read-only**:

- No tool changes data, and none calls the language model, so a client cannot spend your
  daily AI calls.
- The tools run as the token's owner. Ownership is checked exactly as in the REST API; no tool
  takes a user id.

## 1. Create a personal access token

In ApplyPilot, open **Profile > Connect Claude (MCP)**, give the token a name (for example
"Claude Code on my laptop") and **Create token**. Copy it at once: it starts with
`ap_pat_` and is shown only once. ApplyPilot keeps only a hash of it.

You can have up to 5 active tokens. Revoke one from the same page when a device is lost
or no longer used; it stops working immediately.

The server URL is the API's address plus `/mcp`:

- local: `http://localhost:8080/mcp`
- deployed: `https://<your-render-service>.onrender.com/mcp`

## 2a. Claude Code

```bash
claude mcp add --transport http applypilot https://<your-render-service>.onrender.com/mcp \
  --header "Authorization: Bearer ap_pat_..."
```

Then, in Claude Code, `/mcp` should list `applypilot` as connected, with its two tools.

## 2b. Claude Desktop

Claude Desktop's custom connectors expect OAuth. A server that uses a fixed bearer token
connects through the `mcp-remote` bridge (Node.js required). Add this to
`claude_desktop_config.json` (Settings > Developer > Edit Config) and restart Claude
Desktop:

```json
{
  "mcpServers": {
    "applypilot": {
      "command": "npx",
      "args": [
        "-y", "mcp-remote",
        "https://<your-render-service>.onrender.com/mcp",
        "--header", "Authorization:${APPLYPILOT_TOKEN}"
      ],
      "env": { "APPLYPILOT_TOKEN": "Bearer ap_pat_..." }
    }
  }
}
```

The token goes in the `env` block of your local config file. Never put it in the repository
or share the file.

## Security notes

- **A login token does not open `/mcp`, and a personal access token opens nothing else.**
  The MCP endpoint has its own security chain, which accepts only personal access tokens,
  and those tokens are not accepted by the rest of the API.
- **Tool results include text from job postings and CVs.** For the model in your MCP client,
  that text is untrusted data, just as it is for ApplyPilot's own agents. ApplyPilot's server
  instructions say so, but your client decides what to do with it.
- **On the free demo, the server sleeps when idle.** The first call after a quiet period can
  take a minute.
