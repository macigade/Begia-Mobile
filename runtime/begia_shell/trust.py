"""The keys a payload may be signed with.

This is the phone's trust root for software updates, and it lives in the
APK on purpose: a payload cannot add a key that then vouches for itself.
Adding or retiring a key is a new APK.

Each entry is the key's id (the first 16 hex digits of the public key's
sha256) to its name and raw Ed25519 public key, hex. The line to paste
here comes from the desktop repo's tools/payload_keys.py.

Whether an UNSIGNED payload is refused is a per-phone policy (policy.json,
"require_signed", set from the "This phone" card), off until a site asks
for it - but a payload that IS signed is always checked, and one signed by
a key not listed here is reported as untrusted whatever the policy.
"""

TRUSTED_KEYS = {
    # the development key on the build VM (2026-09-11); a site's release key
    # is a second line here, from tools/payload_keys.py in the desktop repo
    "41f1f2855a0c4667": {"name": "sarralle-dev", "public": "b563105607666502f0e7966c6f61c659529a8641009354268518e0a1f8272027"},
}
