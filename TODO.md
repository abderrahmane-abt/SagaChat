# TODO — Plugin System + Websearch

## Plugin System (in-app, no sideloading)

- [ ] `Plugin` interface + `ToolDef` / `ToolCall` / `ToolResult` data models
- [ ] `PluginRegistry` singleton — built-in plugins registered in code, no dynamic load
- [ ] `PluginPrefsRepository` (HXS-backed) — enable/disable + per-plugin config storage
- [ ] Plugin Hub screen — list with enable toggles + tap-to-open settings UI per plugin

## WebPlugin (first concrete plugin)

- [ ] `WebPlugin` class — id=`web`, registers `web_search` + `web_scrape` tools
- [ ] WebPlugin settings composable (impersonate profile, region, max results, cache TTL)
- [ ] Query result cache (HXS-encrypted, TTL 10 min, LRU 64 entries)
- [ ] Rate limiter (max 10 q/min, exponential backoff on 429/202, lite.ddg fallback already in C++)

## Chat integration

- [ ] Extend `Message` model with `toolCalls: List<ToolCall>` + `toolResults: List<ToolResult>`
- [ ] Inject `web_search` tool into prompt when `webSearchEnabled && WebPlugin.enabled`
- [ ] Parse model tool call, execute via plugin, inject result back into conversation
- [ ] Render citations inline in message bubble (title + URL chips)

## UI touches

- [ ] Globe toggle in bottom bar on HomeScreen route — wires existing `_webSearchEnabled`
- [ ] Globe icon in `TnIcons` (from Tabler icon set)
- [ ] Plugin Hub entry point from drawer quick-link (replace or add)
