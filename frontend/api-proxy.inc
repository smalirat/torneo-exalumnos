# Forwarding to the backend, shared by the /api locations of nginx.conf (".inc": nginx does not load it by itself)
proxy_pass $api;
proxy_set_header Host $host;
proxy_set_header X-Real-IP $remote_addr;
proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
proxy_set_header X-Forwarded-Proto $scheme;

# The backend sends its own security headers; nginx adds the site-wide ones, so drop the backend's to avoid duplicates
proxy_hide_header Content-Security-Policy;
proxy_hide_header X-Frame-Options;
proxy_hide_header X-Content-Type-Options;
proxy_hide_header Referrer-Policy;
proxy_hide_header Strict-Transport-Security;
