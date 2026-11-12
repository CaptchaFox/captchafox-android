package com.captchafox.android.publicinterface;

import android.content.Context;

import com.captchafox.android.CaptchaFoxConfiguration;
import com.captchafox.android.CaptchaFoxDialog;
import com.captchafox.android.CaptchaFoxException;
import com.captchafox.android.CaptchaFoxListener;
import com.captchafox.android.CaptchaFoxView;

/** Compiled, never run: the interface as an app in Java uses it. */
@SuppressWarnings("unused")
final class JavaUsage {
    private JavaUsage() {
    }

    static void use(Context context) {
        CaptchaFoxConfiguration minimal = new CaptchaFoxConfiguration("sk_11111111000000001111111100000000", "https://example.com/");
        CaptchaFoxConfiguration configuration = new CaptchaFoxConfiguration(
                "sk_11111111000000001111111100000000",
                "https://example.com/",
                CaptchaFoxConfiguration.Mode.HIDDEN,
                CaptchaFoxConfiguration.Theme.LIGHT,
                "en");
        String mode = configuration.getMode().getValue();

        CaptchaFoxListener listener = new CaptchaFoxListener() {
            @Override
            public void onVerify(String token) {
            }

            @Override
            public void onError(String message) {
            }
        };

        CaptchaFoxView view = new CaptchaFoxView(context, minimal);
        view.setListener(listener);
        view.setShowsLoadingIndicator(false);
        view.reset();
        view.destroy();

        CaptchaFoxDialog dialog = new CaptchaFoxDialog(context, configuration);
        dialog.setListener(listener);
        dialog.setBackdropColor(0xD9000000);
        dialog.setDismissDelayAfterVerifyMillis(0);
        dialog.show();

        CaptchaFoxException error = new CaptchaFoxException(CaptchaFoxException.Code.CLOSED);
        boolean closed = error.getCode().equals(CaptchaFoxException.Code.CLOSED);
    }
}
