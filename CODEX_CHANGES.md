# Codex Changes / 修改说明

This file documents the changes added by Codex without modifying the original `README.md`.

## Summary (EN)

- Persist the **online-mode (Mojang) UUID** after a premium player logs in, so subsequent joins keep the same UUID.
- Persist and apply **skin/profile properties** (e.g., textures) for premium players, so they join the server with the correct premium skin instead of the default skin.
- Keep related **Entry** records in sync when the profile UUID/properties are updated, avoiding broken mappings after UUID changes.

## 概要（中文）

- 正版玩家登录后，持久化保存 **正版 UUID**，后续进入不会丢失/变回离线 UUID。
- 正版玩家进入服务器时，同步并使用 **正版皮肤属性**（textures 等），避免显示默认皮肤。
- 当 profile 的 UUID/属性更新时，同步更新相关 **Entry** 记录，避免映射丢失。

## Touched Areas

- `api`: player interface extended to update GameProfile properties.
- `auth-yggd`: online authentication flow updates UUID + properties and updates entry mappings.
- `openvc`: database helper and player implementation persist UUID/properties and serve them to the proxy/server events.

