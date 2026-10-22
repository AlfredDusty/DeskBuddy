# Pre-publish checklist

- [x] Removed local Gradle build folders and IDE local settings if present.
- [x] Added `.gitignore` entries for local config and signing keys.
- [x] Ran a basic text scan for common private-key and token patterns.
- [ ] Manually review all files and dependency licenses before making the repository public.
- [ ] Select and add the project license.
- [ ] Build and test Android app and firmware on real hardware.
- [ ] Add final GitHub URL to the app About page and README.
- [ ] Add Play Store link after publication.

Basic scan result: No common private-key/API-token/password patterns detected by the basic scan.

This scan is not a guarantee that all secrets or licensing issues have been found.
