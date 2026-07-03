# Privacy Model

The local Room database is the app's source of truth for imported score data.

The app must not persist or log:

- Cookie values
- Token values
- Raw HTML
- Full authentication URLs
- Input values

Wahlap auth URLs and upload tokens are held only in current UI/app state. They are not written to Room.

Logs and user-visible diagnostic text must be redacted before display or printing. Redaction covers credential fields, authentication URLs, HTML blocks/tags, input values, and token-like API response text.

Upload responses from Diving Fish and LXNS are treated as untrusted diagnostic text and are sanitized before being shown in the app.
