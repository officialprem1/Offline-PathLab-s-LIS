
package com.pathlis.offline;

import android.os.Handler;
import android.os.Looper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public final class SupabaseClient {

    private static final String SUPABASE_URL =
            "https://cgtlwgpjmfnovquoboxm.supabase.co";

    private static final String API_KEY =
            "sb_publishable_1ZsZ00a9yPfqyQu3TBT0zw_NS8MRtv-";

    private static final OkHttpClient CLIENT = new OkHttpClient();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private SupabaseClient() {}

    public static class Profile {
        public final String userId;
        public final String labId;
        public final String fullName;
        public final String role;
        public final String accessToken;

        Profile(String userId, String labId, String fullName,
                String role, String accessToken) {
            this.userId = userId;
            this.labId = labId;
            this.fullName = fullName;
            this.role = role;
            this.accessToken = accessToken;
        }
    }

    public interface LoginCallback {
        void onSuccess(Profile profile);
        void onError(String message);
    }

    public static void login(String email, String password,
                             LoginCallback callback) {
        try {
            JSONObject payload = new JSONObject();
            payload.put("email", email.trim());
            payload.put("password", password);

            RequestBody body = RequestBody.create(
                    payload.toString(),
                    MediaType.parse("application/json; charset=utf-8"));

            Request request = new Request.Builder()
                    .url(SUPABASE_URL + "/auth/v1/token?grant_type=password")
                    .addHeader("apikey", API_KEY)
                    .addHeader("Content-Type", "application/json")
                    .post(body)
                    .build();

            CLIENT.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    error(callback,
                            "Network error. Internet check karke dobara try karo.");
                }

                @Override
                public void onResponse(Call call, Response response)
                        throws IOException {
                    String responseText = response.body() != null
                            ? response.body().string() : "";

                    if (!response.isSuccessful()) {
                        error(callback,
                                "Login failed. Email/password check karo.");
                        return;
                    }

                    try {
                        JSONObject auth = new JSONObject(responseText);
                        String token = auth.getString("access_token");
                        String userId = auth.getJSONObject("user")
                                .getString("id");

                        fetchProfile(userId, token, callback);
                    } catch (Exception e) {
                        error(callback, "Login response samajh nahi aaya.");
                    }
                }
            });
        } catch (Exception e) {
            error(callback, "Login request create nahi ho saki.");
        }
    }

    private static void fetchProfile(String userId, String token,
                                     LoginCallback callback) {
        HttpUrl parsed = HttpUrl.parse(SUPABASE_URL + "/rest/v1/profiles");
        if (parsed == null) {
            error(callback, "Supabase URL invalid hai.");
            return;
        }

        HttpUrl url = parsed.newBuilder()
                .addQueryParameter("id", "eq." + userId)
                .addQueryParameter("select", "id,lab_id,full_name,role")
                .build();

        Request request = new Request.Builder()
                .url(url)
                .addHeader("apikey", API_KEY)
                .addHeader("Authorization", "Bearer " + token)
                .get()
                .build();

        CLIENT.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                error(callback, "Profile load nahi hui. Internet check karo.");
            }

            @Override
            public void onResponse(Call call, Response response)
                    throws IOException {
                String responseText = response.body() != null
                        ? response.body().string() : "";

                if (!response.isSuccessful()) {
                    error(callback,
                            "Profile access nahi hua. Supabase RLS policies check karo.");
                    return;
                }

                try {
                    JSONArray rows = new JSONArray(responseText);
                    if (rows.length() == 0) {
                        error(callback, "Is account ka lab profile nahi mila.");
                        return;
                    }

                    JSONObject row = rows.getJSONObject(0);
                    String role = row.optString("role", "");
                    String labId = row.optString("lab_id", "");

                    if (!"admin".equals(role) && !"staff".equals(role)) {
                        error(callback, "Invalid account role.");
                        return;
                    }

                    if (labId.isEmpty() || "null".equals(labId)) {
                        error(callback, "Account kisi lab se linked nahi hai.");
                        return;
                    }

                    Profile profile = new Profile(
                            userId,
                            labId,
                            row.optString("full_name", ""),
                            role,
                            token);

                    MAIN.post(() -> callback.onSuccess(profile));
                } catch (Exception e) {
                    error(callback, "Profile response read nahi ho saka.");
                }
            }
        });
    }

    private static void error(LoginCallback callback, String message) {
        MAIN.post(() -> callback.onError(message));
    }
}
