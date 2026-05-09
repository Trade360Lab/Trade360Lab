# Alpha Security Scanning

Trade360Lab alpha CI exposes dependency and secret scan results for visibility. The first iteration is soft-fail so release validation can collect findings without blocking unrelated readiness work.

Scans:

- `npm audit` for frontend dependencies.
- `pip-audit` for Python dependencies.
- OWASP Dependency Check for Java dependencies.
- Gitleaks for accidental secret exposure.

High or critical findings should be triaged into follow-up GitHub Issues before a release is promoted beyond alpha validation.
