package roboguice.activity;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

import java.lang.ref.SoftReference;
import java.util.ArrayList;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.util.ActivityController;

import roboguice.RoboGuice;
import roboguice.activity.ActivityInjectionTest.ModuleA.A;
import roboguice.activity.ActivityInjectionTest.ModuleB.B;
import roboguice.activity.ActivityInjectionTest.ModuleC.C;
import roboguice.activity.ActivityInjectionTest.ModuleD.D;
import roboguice.inject.ContextScopedProvider;
import roboguice.inject.InjectExtra;
import roboguice.inject.InjectPreference;
import roboguice.inject.InjectResource;
import roboguice.inject.InjectView;

import com.google.inject.ConfigurationException;
import com.google.inject.Inject;
import com.google.inject.Key;
import com.google.inject.Stage;
import com.google.inject.TypeLiteral;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.preference.Preference;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

@RunWith(RobolectricTestRunner.class)
public class ActivityInjectionTest {

    protected DummyActivity activity;

    @Before
    public void setup() {
        RoboGuice
                .setBaseApplicationInjector(Robolectric.application, Stage.DEVELOPMENT, RoboGuice.newDefaultRoboModule(Robolectric.application), new ModuleA());
        activity = Robolectric.buildActivity(DummyActivity.class)
                .withIntent(new Intent(Robolectric.application, DummyActivity.class).putExtra("foobar", "goober")).create().get();
    }

    @Test
    public void shouldInjectUsingDefaultConstructor() {
        assertThat(activity.emptyString, is(""));
    }

    @Test
    public void shouldInjectView() {
        assertThat(activity.text1, is(activity.findViewById(android.R.id.text1)));
    }

    @Test
    public void shouldInjectStringResource() {
        assertThat(activity.cancel, is("Cancel"));
    }

    @Test
    public void shouldInjectExtras() {
        assertThat(activity.foobar, is("goober"));
    }

    @Test
    public void shouldStaticallyInject() {
        assertThat(A.t, equalTo(""));
    }

    @Test
    public void shouldInjectActivityAndRoboActivity() {
        assertEquals(activity, activity.activity);
        assertEquals(activity, activity.roboActivity);
    }

    @Test(expected = ConfigurationException.class)
    public void shouldNotStaticallyInjectViews() {
        RoboGuice
                .setBaseApplicationInjector(Robolectric.application, Stage.DEVELOPMENT, RoboGuice.newDefaultRoboModule(Robolectric.application), new ModuleB());
        Robolectric.buildActivity(B.class).create().get();
    }

    @Test(expected = ConfigurationException.class)
    public void shouldNotStaticallyInjectExtras() {
        RoboGuice
                .setBaseApplicationInjector(Robolectric.application, Stage.DEVELOPMENT, RoboGuice.newDefaultRoboModule(Robolectric.application), new ModuleD());
        Robolectric.buildActivity(D.class).create().get();
    }

    @Test(expected = ConfigurationException.class)
    public void shouldNotStaticallyInjectPreferenceViews() {
        RoboGuice
                .setBaseApplicationInjector(Robolectric.application, Stage.DEVELOPMENT, RoboGuice.newDefaultRoboModule(Robolectric.application), new ModuleC());
        Robolectric.buildActivity(C.class).create().get();
    }

    @Test
    public void shouldInjectApplication() {
        final G g = Robolectric.buildActivity(G.class).create().get();
        assertThat(g.application, equalTo(Robolectric.application));
    }

    @Test
    public void shouldAllowBackgroundThreadsToFinishUsingContextAfterOnDestroy() throws Exception {
        ActivityController<F> fController = Robolectric.buildActivity(F.class);
        final SoftReference<F> ref = new SoftReference<F>(fController.get());
        fController.create();
        fController = null;

        final BlockingQueue<Context> queue = new ArrayBlockingQueue<Context>(1);
        new Thread() {
            final Context context = RoboGuice.getInjector(ref.get()).getInstance(Context.class);

            @Override
            public void run() {
                queue.add(context);
            }
        }.start();

        ref.get().onDestroy();

        // Force an OoM
        // http://stackoverflow.com/questions/3785713/how-to-make-the-java-system-release-soft-references/3810234
        boolean oomHappened = false;
        try {
            @SuppressWarnings({ "MismatchedQueryAndUpdateOfCollection" })
            final ArrayList<Object[]> allocations = new ArrayList<Object[]>();
            int size;
            while ((size = Math.min(Math.abs((int) Runtime.getRuntime().freeMemory()), Integer.MAX_VALUE)) > 0)
                allocations.add(new Object[size]);

        } catch (OutOfMemoryError e) {
            // great!
            oomHappened = true;
        }

        assertTrue(oomHappened);
        assertNotNull(queue.poll(10, TimeUnit.SECONDS));

    }

    @Test
    public void shouldBeAbleToGetContextProvidersInBackgroundThreads() throws Exception {
        final F f = Robolectric.buildActivity(F.class).create().get();

        final FutureTask<Context> future = new FutureTask<Context>(new Callable<Context>() {
            final ContextScopedProvider<Context> contextProvider = RoboGuice.getInjector(f).getInstance(
                    Key.get(new TypeLiteral<ContextScopedProvider<Context>>() {
                    }));

            @Override
            public Context call() throws Exception {
                return contextProvider.get(f);
            }

        });

        Executors.newSingleThreadExecutor().execute(future);

        future.get();
    }

    public static class DummyActivity extends RoboActivity {
        @Inject
        protected String emptyString;
        @Inject
        protected Activity activity;
        @Inject
        protected RoboActivity roboActivity;
        @InjectView(android.R.id.text1)
        protected TextView text1;
        @InjectResource(android.R.string.cancel)
        protected String cancel;
        @InjectExtra("foobar")
        protected String foobar;

        @Override
        protected void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);

            final LinearLayout root = new LinearLayout(this);

            final TextView text1 = new TextView(this);
            root.addView(text1);
            text1.setId(android.R.id.text1);

            final LinearLayout included1 = addIncludedView(android.R.id.summary, android.R.string.ok);
            root.addView(included1);
            final LinearLayout included2 = addIncludedView(android.R.id.title, android.R.string.no);
            root.addView(included2);

            setContentView(root);
        }

        protected LinearLayout addIncludedView(int includedRootId, int stringResId) {
            LinearLayout container = new LinearLayout(this);
            container.setId(includedRootId);

            TextView textView = new TextView(this);
            container.addView(textView);
            textView.setId(android.R.id.text2);
            textView.setText(stringResId);
            return container;
        }
    }

    public static class ModuleA extends com.google.inject.AbstractModule {
        @Override
        protected void configure() {
            requestStaticInjection(A.class);
        }

        public static class A {
            @InjectResource(android.R.string.cancel)
            static String s;
            @Inject
            static String t;
        }
    }

    public static class ModuleB extends com.google.inject.AbstractModule {
        @Override
        public void configure() {
            requestStaticInjection(B.class);
        }

        public static class B extends RoboActivity {
            @InjectView(0)
            static View v;
        }
    }

    public static class ModuleC extends com.google.inject.AbstractModule {
        @Override
        public void configure() {
            requestStaticInjection(C.class);
        }

        public static class C extends RoboActivity {
            @InjectPreference("xxx")
            static Preference v;

            @Override
            protected void onCreate(Bundle savedInstanceState) {
                super.onCreate(savedInstanceState);
            }
        }
    }

    public static class ModuleD extends com.google.inject.AbstractModule {
        @Override
        public void configure() {
            requestStaticInjection(D.class);
        }

        public static class D extends RoboActivity{
            @InjectExtra("xxx") static String s;
        }
    }

    public static class F extends RoboActivity {}

    public static class PojoA {
        @InjectView(100)
        View v;
    }

    public static class G extends RoboActivity {
        @Inject Application application;
    }
}
