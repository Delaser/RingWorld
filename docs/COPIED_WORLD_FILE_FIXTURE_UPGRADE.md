# Copied-world file-fix fixture upgrade

Minecraft 26.2 can require a one-time file-fix upgrade before opening a 26.1
save. RingWorld's production projection, visual-parity, and lifecycle fixtures
open only a disposable copied world in an explicitly enabled qualification run.

`CopiedWorldFileFixUpgrade` accepts only this exact 26.2 sequence:

1. `BackupConfirmScreen` titled `selectWorld.backupQuestion.file_fixing_required`,
   using its `selectWorld.backupJoinConfirmButton` action; then
2. `ConfirmScreen` titled `upgradeWorld.done` with message `upgradeWorld.joinNow`.

It does not accept downgrade, snapshot, experimental, or unrelated confirmation
screens. The completion action is unavailable until the file-fix backup screen
was accepted in the same fixture instance. The helper has no direct
`BackupConfirmScreen` class linkage, so 26.1 remains a no-op when that flow is
not presented. Timeout records include the current screen class and title for
diagnosis.

The clean `0ca305b` Fabric 26.2 source-ABI diagnostic confirmed both prompts
on 2026-08-27. It opened a copied complete 16,384x256 26.1 world within nine
seconds, preserved the complete Atlas, captured all three projection views,
and exited successfully. The shared helper APIs were also inspected against
26.1, 26.1.1, and 26.1.2 bytecode. Existing bounded fixture timeouts remain;
there was no evidence requiring an increase. Frozen-candidate and NeoForge
runtime qualification remain separate gates.
