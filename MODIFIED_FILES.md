# Files changed in this update

- `app/src/main/java/com/pathlis/offline/MainActivity.java` — removes the hardcoded Java test catalog from report-entry flow; loads shared active tests/parameters from Supabase; caches last successful test configuration and labels cache state; adds Admin test/parameter management (create/edit/activate/deactivate); adds Back/discard confirmation for unsaved report-entry fields; updates navigation menu.
- `app/src/main/java/com/pathlis/offline/SupabaseClient.java` — adds lab-scoped shared test/parameter queries and Admin-only create/edit/activation methods; uses OkHttp for PATCH operations; keeps role checks in code in addition to server RLS.
- `supabase/migrations/202610220001_pathlis_shared_configuration.sql` — additive shared configuration/activity schema, RLS policies, and composite relationship constraint ensuring parameters reference a test from the same lab.
- `.github/workflows/android.yml` — CI build trigger targets `supabase` and `supabase-integration`, not `main`.
- `README.md` — updated setup, shared configuration behavior, and safety limitations.
- `TEST_REPORT.md` — verification performed, not performed, and remaining limitations.
