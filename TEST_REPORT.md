# PathLIS verification report

## Checks performed
- Original ZIP and updated project ZIP were inspected.
- Android manifest and XML resource files parse successfully with Python XML parser.
- Java source delimiters/braces were checked for balanced structure after edits.
- Confirmed the project package/application ID remains `com.pathlis.offline`.
- Added shared test/parameter REST methods and Admin UI paths for create/edit/activate/deactivate.
- Added a same-laboratory composite foreign-key constraint for test parameters in the additive SQL migration.
- Added cached test configuration behavior with an explicit cached/outdated status.
- Added unsaved report-entry confirmation for Android Back.
- Checked that the migration does not drop patient/report tables.

## Not performed / not passed
- **Gradle build / Java compilation:** not performed. The current environment has no Gradle executable, Android SDK platform, or Gradle wrapper; external network access is unavailable to download them.
- APK generation or installation: not performed.
- Live Supabase migration execution: not performed.
- Existing production schema and policies: not available for validation.
- Login with real Admin and Staff accounts: not performed.
- RLS isolation tests with two labs: not performed.
- Cross-device patient/report/config synchronization: not performed.
- PDF document-picker and WhatsApp sharing on a real device: not performed.
- Full regression, lint, automated unit tests, accessibility, lifecycle, or performance tests: not performed.

## Known remaining limitations / risks
- Existing networking still executes synchronously from the UI thread (the legacy app explicitly permits main-thread network access); slow network responses can freeze the UI or cause ANRs. Do not use in production until this is refactored and tested.
- Staff-wise aggregated dashboard metrics are not implemented; the current Staff Activity screen is a basic event list.
- The cache timestamp reflects the latest `updated_at` timestamp among fetched test/parameter rows; there is no single atomic server-side version covering settings plus every test/parameter table.
- Age/sex-specific ranges are stored by the SQL schema but not yet exposed in the Android parameter editor or applied by report entry. Reference interval logic is not validated clinically.
- Patient/report RLS policies from the existing project were not changed or validated because the original live schema/policies were not provided.
- No seeded test catalog is created. Admin must add and clinically validate test/parameter definitions before test selection becomes available.
- Configuration editing and refresh logic has only received static inspection; compile/runtime compatibility is not confirmed until CI or Android Studio build succeeds.

## Safe next verification
1. Run the included GitHub Actions workflow on the active `supabase` branch and fix any compilation errors before installing.
2. Apply the SQL migration first in a non-production Supabase project after comparing types/columns against the live schema.
3. Test separate Admin and Staff accounts and verify cross-lab access is denied.
4. Seed a small, lab-approved test catalog and compare generated PDF values/reference intervals against approved source documents.
5. Test on at least two Android devices, including offline/online transitions, Back/discard, PDF save, and WhatsApp share.
