# Scripts Folder Guide

## Scope
This folder contains operational scripts for local/home deployment and diagnostics.

## Deployment Safety Rule (Mandatory)
- Never overwrite deployed configs without directly asking the user for confirmation first.
- Regular deployment (jar-only, no config sync) is always acceptable and should remain the default path.
- Any config sync/replace path must require explicit confirmation in the script prompt (or an explicit override env var intended for non-interactive runs).

## Context DB Diagnostics
Use `context-db-diagnostics.sh` for quick triage of:
- session growth,
- repeated tool failures,
- timeout-like incidents,
- empty assistant/model-turn patterns.

## SQL Incident Window Query
Use `sql/context-db-incident-windows.sql` to get one row per tool failure with adjacent context previews for fast root-cause tracing.
