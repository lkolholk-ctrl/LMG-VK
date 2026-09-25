# VPN bypass routing

The VK proxy remains installed for API, media downloads and artwork. Its host
rewriting, endpoints, certificate handling and configuration are unchanged.
VPN bypass selects the Android network used to reach that existing transport.

The previous implementation only registered a passive physical-network callback
and bound the process. It did not retain the physical network with requestNetwork,
explicitly bind OkHttp sockets/DNS, or cancel active Ktor calls when reviving the
shared connection pool. Default-network notifications could also revive playback
even while its selected physical route remained unchanged.

The updated implementation:

- Loads the saved bypass setting before initializing route management.
- Requests an INTERNET/NOT_VPN network while bypass is enabled, releasing the
  request when disabled; CHANGE_NETWORK_STATE is a normal manifest permission.
- Excludes known blocked and suspended candidates, prefers validated networks,
  and keeps the current route when candidates otherwise have equal priority.
- Checks the actual process binding instead of trusting only a cached Network.
- Uses the selected Network socket factory and DNS for both VK Ktor and Coil,
  retaining process binding for existing URLConnection/native consumers.
- Cancels old API/media and artwork calls and evicts idle pooled connections
  when the effective route changes. Existing request retry policy is unchanged.
- Deduplicates default-network notifications against the effective route so VPN
  reconnects do not reset playback unnecessarily while its physical route is stable.

Unit tests cover route selection and socket-factory forwarding across route
changes. These do not establish the cause of a particular phone's failure or
verify Android VPN policy. A VPN that forbids bypass or Android lockdown can
still prevent access to physical networks.

Device verification: launch with VPN already enabled, reconnect VPN while the
app is open, switch Wi-Fi/SIM, toggle the bypass setting, then check VK lists,
artwork and playback. Keep the existing VK proxy enabled throughout.
