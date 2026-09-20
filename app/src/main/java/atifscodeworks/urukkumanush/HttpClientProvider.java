package atifscodeworks.urukkumanush;

import java.util.concurrent.TimeUnit;
import okhttp3.Dns;
import okhttp3.OkHttpClient;

public class HttpClientProvider {
    private static volatile OkHttpClient sClient;

    public static OkHttpClient get() {
        if (sClient == null) {
            synchronized (HttpClientProvider.class) {
                if (sClient == null) {
                    sClient = new OkHttpClient.Builder()
                            .dns(Dns.SYSTEM)
                            .followRedirects(true)
                            .followSslRedirects(true)
                            .connectTimeout(15, TimeUnit.SECONDS)
                            .readTimeout(30, TimeUnit.SECONDS)
                            .writeTimeout(30, TimeUnit.SECONDS)
                            .build();
                }
            }
        }
        return sClient;
    }
}
