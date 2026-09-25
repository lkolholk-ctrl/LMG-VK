# Password-stage authorization investigation

The user reports that phone entry, captcha and SMS succeed, but password submission
returns "too many login attempts"; a fresh VK X login works. The underlying server
reason is not yet confirmed. No credentials were submitted during this investigation.

The APK downloaded from vkx.app's public update link on 2026-09-07 is byte-identical
to the existing VK X 8.14.1 build 100136. In its current local decompilation,
`C19419l` case 19 passes the SID returned by `ecosystem.checkOtp` into `C1696l`
with its confirmed-phone flag set. Case 21 passes that flag and the entered password
to `C14467l.m4685l`; `m4694l` selects `phone_confirmation_sid`. LMG uses the same
SID transition and grant type.

The optional OAuth `code` is null on this path. `C18159l.yandex` omits null values,
as does `VkMethod.param` in LMG. Earlier notes claiming that an empty `code` must
always be sent were incorrect; no request-field change was made on that basis.

The diagnostic build preserves the server error/type and selected grant in the
on-screen password rejection. Unknown OAuth errors now retain `error_type` too.
The diagnostic suffix contains no request credentials or session identifiers.
If the in-memory login attempt disappears while an SMS/password screen still has
a SID, LMG reports an expired session instead of silently starting another login.

Proxy settings, client identity, request builders, and lyrics rendering are unchanged.
The next device rejection's diagnostic suffix is needed to distinguish the observed
failure from a generic human-readable error. This build is not a confirmed fix for
the server's login restriction.
