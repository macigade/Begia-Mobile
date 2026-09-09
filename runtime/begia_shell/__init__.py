"""The Python side of the BEGIA Android shell.

Two modules, standard library only, because they run inside the APK before
any payload is on the path and the laptop tests run them unchanged:

    payload   verify a .begia, install it into a slot, decide which slot boots
              and roll back a build that never became healthy
    boot      put a slot on sys.path, start the service, wait until it answers

SHELL_VERSION in payload.py is the number a payload's `min_shell` is compared
against. Bump it when the APK changes in a way a payload can depend on - a
new wheel, a new permission, a new Python - and never otherwise.
"""
