# SearXNG Deployment For Magenta

## Purpose

Document the direct systemd SearXNG deployment expected by Magenta's native `web_search` and `web_fetch` tools.

## Host Layout

- Host: `192.168.1.112`
- Service account: `searxng`
- Source checkout: `/usr/local/searxng/searxng-src`
- Virtualenv: `/usr/local/searxng/searx-pyenv`
- Runtime config: `/etc/searxng/settings.yml`
- Service: `searxng.service`
- LAN URL: `http://192.168.1.112:8888`

## Install Commands

Run these on `admin2@192.168.1.112` with sudo access.

```bash
sudo apt-get update
sudo apt-get install -y git python3 python3-dev python3-babel python3-venv python-is-python3 python3-pip build-essential libxslt-dev zlib1g-dev libffi-dev libssl-dev curl openssl
sudo useradd --system --home-dir /usr/local/searxng --shell /bin/bash --comment 'Privacy-respecting metasearch engine' searxng || true
sudo mkdir -p /usr/local/searxng /etc/searxng
sudo chown -R searxng:searxng /usr/local/searxng
sudo -u searxng git clone https://github.com/searxng/searxng.git /usr/local/searxng/searxng-src
sudo -u searxng python3 -m venv /usr/local/searxng/searx-pyenv
sudo -u searxng /usr/local/searxng/searx-pyenv/bin/pip install -U pip setuptools wheel pyyaml msgspec typing-extensions pybind11
cd /usr/local/searxng/searxng-src
sudo -u searxng /usr/local/searxng/searx-pyenv/bin/pip install --use-pep517 --no-build-isolation -e .
```

Generate a secret locally on the host:

```bash
openssl rand -hex 32
```

Create `/etc/searxng/settings.yml`:

```yaml
use_default_settings: true

server:
  bind_address: "0.0.0.0"
  port: 8888
  secret_key: "replace-with-generated-secret"
  limiter: false
  image_proxy: true

search:
  formats:
    - html
    - json
```

Create `/etc/systemd/system/searxng.service`:

```ini
[Unit]
Description=SearXNG metasearch engine
After=network.target

[Service]
Type=simple
User=searxng
Group=searxng
WorkingDirectory=/usr/local/searxng/searxng-src
Environment=SEARXNG_SETTINGS_PATH=/etc/searxng/settings.yml
ExecStart=/usr/local/searxng/searx-pyenv/bin/python searx/webapp.py
Restart=on-failure
RestartSec=5

[Install]
WantedBy=multi-user.target
```

Enable and start:

```bash
sudo systemctl daemon-reload
sudo systemctl enable --now searxng
sudo systemctl status searxng
```

If the host firewall is active, allow LAN access to port `8888`. For UFW:

```bash
sudo ufw allow from 192.168.1.0/24 to any port 8888 proto tcp
```

## Magenta Config

The active local app config path is defined in `src/main/resources/application.yml`:

```yaml
app:
  ai:
    config-path: ./config/ai-config.example.json
```

The loaded AI config should include:

```json
"webSearch": {
  "enabled": true,
  "provider": "searxng",
  "baseUrl": "http://192.168.1.112:8888"
}
```

Restart Magenta after changing this file if the app is running.

## Validation

On the SearXNG host:

```bash
systemctl status searxng
curl 'http://127.0.0.1:8888/search?q=ollama&format=json'
```

From the Magenta client machine:

```bash
curl 'http://192.168.1.112:8888/search?q=ollama&format=json'
```

In Magenta:

- Confirm the configured default agent has `web_search` and `web_fetch` in its resolved tool registry.
- Ask a current-information question and confirm the assistant uses `web_search`, optionally follows a result with `web_fetch`, and cites result URLs.
- Run `./mvnw test` after repository config or tool changes.

## Troubleshooting

- `403` or missing JSON output usually means `search.formats` does not include `json`.
- Connection refused usually means the service is not listening on port `8888` or failed to start.
- Client timeout from another machine usually means `bind_address` is still loopback-only or the host firewall blocks port `8888`.
- Empty or low-quality results are usually engine-rate-limit or engine-selection issues; inspect `/etc/searxng/settings.yml` and the systemd journal.
- If `apt-get update` fails before installation, inspect `/etc/apt/sources.list.d/` for unrelated third-party repositories that do not support the host's Ubuntu release.

```bash
journalctl -u searxng -n 200 --no-pager
ss -ltnp | grep 8888
```

## Optional Tuning

- Tune enabled engines only after the baseline JSON API is stable.
- Add a reverse proxy or authentication only if Magenta needs access beyond the trusted LAN.
- Consider moving Magenta to a non-example config filename when the deployment becomes permanent.
