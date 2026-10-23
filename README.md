# Path LIS — updated source package

This is the existing Java Android project (`com.pathlis.offline`), not a replacement template. It uses Android SDK 35, min SDK 23, AndroidX Core, OkHttp, Supabase Auth/PostgREST, SQLite and Android `PdfDocument`/`FileProvider`.

## Current changes in this package

- Adds an app navigation menu for existing screens and admin-only settings/activity screens.
- Adds shared laboratory settings read/upsert methods in Supabase. Settings are cached locally only after a successful server response; a failed refresh reports whether the app is showing previously cached settings.
- Adds best-effort activity events for login, logout, patient creation, report creation and lab-settings changes.
- Adds an additive Supabase SQL migration for shared settings, test/parameter configuration tables and activity records, with lab-scoped RLS.
- Keeps the existing patient/report SQLite tables, report generation, document picker and FileProvider configuration.

## Important limitations — read before clinical use

This is **not yet a fully accepted implementation of every item in the supplied master prompt**. In particular, the existing test/parameter selector still reads its legacy Java definitions. The new `lab_tests` and `lab_test_parameters` tables are not yet wired into the result-entry UI or a complete admin CRUD editor. The activity screen is a basic event list, not a full staff-wise analytics dashboard. Network calls in the existing app still execute synchronously. The existing SQL schema and live RLS policies were not supplied, so the migration has not been validated against your production database. Do not use this build for clinical reporting until the ranges, database schema and security policies have been reviewed and tested.

## Apply the SQL migration safely

1. Make a Supabase database backup / confirm point-in-time recovery is available.
2. Open Supabase Dashboard → SQL Editor for the **existing project**.
3. Inspect `public.profiles` and confirm it has `id`, `lab_id`, and `role`, and that `lab_id` is UUID-compatible. The current Android login uses those profile fields.
4. Inspect the existing `lab_id` data type in `patients` and `reports`. The migration currently assumes UUID lab IDs. Stop and adapt the migration if the live types differ.
5. Review `supabase/migrations/202610220001_pathlis_shared_configuration.sql` in a non-production project first. It creates new tables and policies and does not drop patient/report tables.
6. Run the migration in the test project, verify Admin and Staff access with separate accounts, then apply to production only after approval.
7. Insert one `lab_settings` row for each lab using its exact existing `lab_id`. An Admin can then update the settings in the app. No test/parameter rows are seeded automatically; clinical values must be added and validated by the lab before any future UI wiring.
8. Verify RLS in the Supabase policy editor and with two labs. Do not add a service-role key to the Android app.

## Open and build in Android Studio

1. Extract the ZIP.
2. Android Studio → **Open** → select the folder containing `settings.gradle.kts`.
3. Install JDK 17 and Android SDK Platform 35 / Build Tools 35.0.0 when prompted.
4. Let Gradle sync. This ZIP has no Gradle wrapper; Android Studio may use its configured Gradle installation. The project uses Android Gradle Plugin 8.9.2, so use a compatible Gradle 8.x version (the CI workflow uses 8.11.1).
5. Select `app` → `assembleDebug`, or run `gradle assembleDebug` from the project root with a compatible Gradle installation.
6. The expected debug APK output path is `app/build/outputs/apk/debug/app-debug.apk` **if the build succeeds**. No APK is included in this package because a build could not be run in the current environment.

## Supabase configuration

`SupabaseClient.java` contains the existing project URL and publishable client key supplied in the original ZIP. A publishable/anon key is not a service-role secret, but database safety depends on RLS. Never place a service-role key in the APK. Rotate the publishable key only if you suspect it has been misused, and update the app accordingly.

## Verification report

See `TEST_REPORT.md` for checks actually performed and those not performed.

## Shared test/parameter configuration update

The current source now loads active test definitions and parameters from `lab_tests` and `lab_test_parameters` after login and on manual refresh. Admins can add tests, edit test names/order, add/edit parameters, and activate/deactivate tests and parameters from **Test & Parameter Management** in the navigation menu. Staff read definitions only; Supabase RLS remains the actual authorization boundary. Test definitions are cached locally after a successful response; the dashboard labels cached data as potentially outdated when a refresh fails. The old Java hardcoded test list has been removed from the report-entry path, so no configured active shared test means no test can be selected for a new report.

Important: these test definitions and reference intervals are lab-managed clinical configuration. They must be reviewed and approved by qualified lab staff before use. This code has not been compiled or tested against a live Supabase instance in this environment. Existing network operations still execute synchronously in the Activity, which can cause UI stalls/ANRs on slow connections; a full background-thread refactor and device QA remain required before production use.
