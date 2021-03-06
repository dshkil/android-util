package com.shkil.android.util.net;

import android.support.annotation.Nullable;
import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.concurrent.TimeUnit;

import static com.shkil.android.util.Utils.isEmpty;
import static com.shkil.android.util.Utils.isNotEmpty;
import static java.lang.System.currentTimeMillis;

public class AccessToken {

    private static final String TAG = "AccessToken";

    public static final AccessToken NULL = new AccessToken(null, null);

    private final String type;
    private final String accessToken;
    private long expiresAt;

    public static AccessToken bearer(String accessToken) {
        if (isEmpty(accessToken)) {
            throw new IllegalArgumentException("accessToken is empty");
        }
        return new AccessToken("Bearer", accessToken);
    }

    public static AccessToken bearerOrNull(String accessToken) {
        if (isEmpty(accessToken)) {
            return NULL;
        }
        return new AccessToken("Bearer", accessToken);
    }

    public AccessToken(String type, String accessToken) {
        this.type = type;
        this.accessToken = accessToken;
    }

    public AccessToken setNeverExpires() {
        return setExpiresAt(-1);
    }

    public AccessToken setExpiresAt(long expiresAt) {
        this.expiresAt = expiresAt;
        return this;
    }

    public AccessToken setExpiresIn(long expiresIn, TimeUnit timeUnit) {
        if (expiresIn > 0) {
            this.expiresAt = currentTimeMillis() + timeUnit.toMillis(expiresIn);
        } else {
            this.expiresAt = expiresIn;
        }
        return this;
    }

    public String getType() {
        return type;
    }

    public String getAccessToken() {
        return accessToken;
    }

    public long getExpiresAt() {
        if (this == NULL) {
            return 0;
        }
        return expiresAt;
    }

    public String toAuthorizationHeaderValue() {
        if (isEmpty(type) && isEmpty(accessToken)) {
            throw new IllegalStateException("null type and token");
        }
        return type + " " + accessToken;
    }

    public String toSerializedString() {
        try {
            return new JSONObject()
                    .put("type", type)
                    .put("accessToken", accessToken)
                    .put("expiresAt", expiresAt)
                    .toString();
        } catch (JSONException ex) {
            throw new RuntimeException(ex);
        }
    }

    @Nullable
    public static AccessToken fromSerializedString(String value) {
        if (isNotEmpty(value)) {
            try {
                JSONObject json = new JSONObject(value);
                String type = json.getString("type");
                String accessToken = json.getString("accessToken");
                long expiresAt = json.getLong("expiresAt");
                return new AccessToken(type, accessToken).setExpiresAt(expiresAt);
            } catch (JSONException ex) {
                Log.e(TAG, "Can't parse access token", ex);
            }
        }
        return null;
    }

    @Override
    public String toString() {
        if (this == NULL) {
            return "AccessToken{NULL}";
        }
        return "AccessToken{" +
                "type='" + type + '\'' +
                ", accessToken='" + accessToken + '\'' +
                ", expiresAt=" + expiresAt +
                '}';
    }
}
