package sh.nothing.gyazo;

import android.app.Application;

import com.deploygate.sdk.DeployGate;

/**
 * Created by tnj on 6/26/15.
 */
public class App extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        DeployGate.install(this, null, true);
    }
}
