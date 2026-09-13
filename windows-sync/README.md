# ExpenseTracker Windows local backup

This optional companion keeps a local backup of your ExpenseTracker data on your Windows laptop. It uses no Google account, Firebase, or cloud storage.

## Start the backup server

Open PowerShell in this folder:

    powershell -ExecutionPolicy Bypass -File .\ExpenseTrackerBackup.ps1 -Token "CHANGE-ME"

Keep the PowerShell window running.

The server listens on port 8765 and writes:
- backups/latest.json
- backups/history/backup-YYYYMMDD-HHMMSS.json

## Connect the Android app

1. On Windows run `ipconfig`.
2. Find the Wi-Fi adapter's IPv4 address, for example `192.168.1.10`.
3. In ExpenseTracker open Settings -> Automatic laptop backup.
4. Enter `http://192.168.1.10:8765/backup`.
5. Enter the same token used to start the server.
6. Turn on Automatic backup and press Save.

The Android app uses WorkManager and attempts a backup at least every 15 minutes while connected to an unmetered network. Android can defer the exact execution time.

## Security

This server is intended for a trusted home LAN. The token authenticates requests, but this implementation uses HTTP rather than TLS. Do not expose port 8765 to the public Internet or use it on an untrusted network.

The backup is a JSON export containing your expense data. Keep the Windows backup folder private.
