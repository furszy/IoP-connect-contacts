package org.furszy.contacts;

import android.app.ActivityManager;
import android.app.Application;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Environment;
import android.support.v4.content.LocalBroadcastManager;

import org.fermat.redtooth.services.chat.ChatModule;
import org.fermat.redtooth.services.interfaces.PairingModule;
import org.fermat.redtooth.services.interfaces.ProfilesModule;
import org.furszy.contacts.ui.home.HomeActivity;

import org.fermat.redtooth.core.IoPConnectContext;
import org.fermat.redtooth.services.EnabledServices;
import org.fermat.redtooth.profile_server.ProfileServerConfigurations;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.android.LogcatAppender;
import ch.qos.logback.classic.encoder.PatternLayoutEncoder;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.rolling.RollingFileAppender;
import ch.qos.logback.core.rolling.TimeBasedRollingPolicy;
import iop.org.iop_sdk_android.core.ClientServiceConnectHelper;
import iop.org.iop_sdk_android.core.InitListener;
import iop.org.iop_sdk_android.core.service.ProfileServerConfigurationsImp;
import iop.org.iop_sdk_android.core.service.client_broker.ConnectClientService;
import iop.org.iop_sdk_android.core.service.modules.imp.chat.ChatIntentsConstants;

import static iop.org.iop_sdk_android.core.IntentBroadcastConstants.ACTION_IOP_SERVICE_CONNECTED;
import static iop.org.iop_sdk_android.core.IntentBroadcastConstants.ACTION_ON_CHECK_IN_FAIL;
import static iop.org.iop_sdk_android.core.IntentBroadcastConstants.ACTION_ON_PAIR_RECEIVED;
import static iop.org.iop_sdk_android.core.IntentBroadcastConstants.ACTION_ON_PROFILE_CONNECTED;
import static iop.org.iop_sdk_android.core.IntentBroadcastConstants.ACTION_ON_PROFILE_DISCONNECTED;
import static iop.org.iop_sdk_android.core.IntentBroadcastConstants.ACTION_ON_RESPONSE_PAIR_RECEIVED;
import static iop.org.iop_sdk_android.core.IntentBroadcastConstants.INTENT_EXTRA_PROF_KEY;
import static iop.org.iop_sdk_android.core.IntentBroadcastConstants.INTENT_EXTRA_PROF_NAME;
import static iop.org.iop_sdk_android.core.IntentBroadcastConstants.INTENT_RESPONSE_DETAIL;

/**
 * Created by furszy on 5/25/17.
 */

public class App extends Application implements IoPConnectContext {

    public static final String INTENT_ACTION_ON_SERVICE_CONNECTED = "service_connected";
    public static final String INTENT_ACTION_PROFILE_CONNECTED = "profile_connected";
    public static final String INTENT_ACTION_PROFILE_CHECK_IN_FAIL= "profile_check_in_fail";
    public static final String INTENT_ACTION_PROFILE_DISCONNECTED = "profile_disconnected";

    public static final String INTENT_EXTRA_ERROR_DETAIL = "error_detail";

    public static final String INTENT_CHAT_ACCEPTED_BROADCAST = "chat_accepted";
    public static final String INTENT_CHAT_REFUSED_BROADCAST = "chat_refused";
    public static final String INTENT_CHAT_TEXT_BROADCAST = "chat_text";
    public static final String INTENT_CHAT_TEXT_RECEIVED = "text";

    /** Preferences */
    private static final String PREFS_NAME = "app_prefs";

    private static Logger log;
    private static App instance;

    private ActivityManager activityManager;
    private PackageInfo info;

    private ClientServiceConnectHelper connectHelper;
    private LocalBroadcastManager broadcastManager;
    private NotificationManager notificationManager;
    private long timeCreateApplication = System.currentTimeMillis();

    // App's modules
    private ProfilesModule profilesModule;
    private ChatModule chatModule;
    private PairingModule pairingModule;

    /** Pub key of the selected profile */
    private String selectedProfilePubKey;
    private AppConf appConf;


    public static App getInstance() {
        return instance;
    }

    private BroadcastReceiver serviceReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action.equals(ACTION_ON_PAIR_RECEIVED)){
                String pubKey = intent.getStringExtra(INTENT_EXTRA_PROF_KEY);
                String name = intent.getStringExtra(INTENT_EXTRA_PROF_NAME);
                onPairReceived(pubKey,name);
            }else if (action.equals(ACTION_ON_RESPONSE_PAIR_RECEIVED)){
                String pubKey = intent.getStringExtra(INTENT_EXTRA_PROF_KEY);
                String responseDetail = intent.getStringExtra(INTENT_RESPONSE_DETAIL);
                onPairResponseReceived(pubKey,responseDetail);
            }else if (action.equals(ACTION_ON_PROFILE_CONNECTED)){
                String profPubKey = intent.getStringExtra(INTENT_EXTRA_PROF_KEY);
                onConnect(profPubKey);
            }else if (action.equals(ACTION_ON_PROFILE_DISCONNECTED)){
                onDisconnect();
            }else if (action.equals(ACTION_ON_CHECK_IN_FAIL)){
                String detail = intent.getStringExtra(INTENT_RESPONSE_DETAIL);
                onCheckInFail(detail);
            }
        }
    };

    private ChatModuleReceiver chatModuleReceiver = new ChatModuleReceiver();

    @Override
    public void onCreate() {
        super.onCreate();
        try {
            instance = this;
            initLogging();
            log = LoggerFactory.getLogger(App.class);
            PackageManager manager = getPackageManager();
            info = manager.getPackageInfo(this.getPackageName(), 0);
            activityManager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
            CrashReporter.init(getCacheDir());
            appConf = new AppConf(getSharedPreferences(PREFS_NAME, 0));
            selectedProfilePubKey = appConf.getSelectedProfPubKey();
            broadcastManager = LocalBroadcastManager.getInstance(this);
            notificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            registerReceiver(chatModuleReceiver,new IntentFilter(ChatIntentsConstants.ACTION_ON_CHAT_CONNECTED));
            registerReceiver(chatModuleReceiver,new IntentFilter(ChatIntentsConstants.ACTION_ON_CHAT_DISCONNECTED));
            registerReceiver(chatModuleReceiver,new IntentFilter(ChatIntentsConstants.ACTION_ON_CHAT_MSG_RECEIVED));
            // register broadcast listeners
            broadcastManager.registerReceiver(serviceReceiver, new IntentFilter(ACTION_ON_PAIR_RECEIVED));
            broadcastManager.registerReceiver(serviceReceiver, new IntentFilter(ACTION_ON_RESPONSE_PAIR_RECEIVED));
            broadcastManager.registerReceiver(serviceReceiver,new IntentFilter(ACTION_ON_PROFILE_CONNECTED));
            broadcastManager.registerReceiver(serviceReceiver,new IntentFilter(ACTION_ON_PROFILE_DISCONNECTED));

            connectHelper = ClientServiceConnectHelper.init(this, new InitListener() {
                @Override
                public void onConnected() {
                    try {
                        // notify connection
                        Intent intent = new Intent(ACTION_IOP_SERVICE_CONNECTED);
                        broadcastManager.sendBroadcast(intent);

                        ExecutorService executors = Executors.newSingleThreadExecutor();
                        executors.submit(new Runnable() {
                            @Override
                            public void run() {
                                try {
                                    ConnectClientService module = connectHelper.getClient();

                                    profilesModule = (ProfilesModule) module.getModule(EnabledServices.PROFILE_DATA);
                                    pairingModule = (PairingModule) module.getModule(EnabledServices.PROFILE_PAIRING);
                                    chatModule = (ChatModule) module.getModule(EnabledServices.CHAT);

                                    // notify connection to the service
                                    Intent notificateIntent = new Intent(INTENT_ACTION_ON_SERVICE_CONNECTED);
                                    broadcastManager.sendBroadcast(notificateIntent);

                                    /*if (module.isIdentityCreated()) {
                                        log.info("Trying to connect profile");
                                        Profile profile = module.getProfile();
                                        if (profile != null) {
                                            module.connect(profile.getHexPublicKey());
                                        } else
                                            Log.i("App", "Profile not found to connect");
                                    }*/
                                } catch (Exception e) {
                                    e.printStackTrace();
                                }
                            }
                        });
                        executors.shutdown();
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }

                @Override
                public void onDisconnected() {

                }
            });
        }catch (Exception e){
            e.printStackTrace();
            // check here...
        }


    }

    @Override
    public ProfileServerConfigurations createProfSerConfig() {
        ProfileServerConfigurationsImp conf = new ProfileServerConfigurationsImp(this,getSharedPreferences(ProfileServerConfigurationsImp.PREFS_NAME,0));
        conf.setHost(AppConstants.TEST_PROFILE_SERVER_HOST);//"192.168.0.10");
        return conf;
    }


    private void initLogging() {

        final File logDir = getDir("log", MODE_PRIVATE);
        final File logFile = new File(logDir, "app.log");

        final LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();

        final PatternLayoutEncoder filePattern = new PatternLayoutEncoder();
        filePattern.setContext(context);
        filePattern.setPattern("%d{HH:mm:ss,UTC} [%thread] %logger{0} - %msg%n");
        filePattern.start();

        final RollingFileAppender<ILoggingEvent> fileAppender = new RollingFileAppender<ILoggingEvent>();
        fileAppender.setContext(context);
        fileAppender.setFile(logFile.getAbsolutePath());

        final TimeBasedRollingPolicy<ILoggingEvent> rollingPolicy = new TimeBasedRollingPolicy<ILoggingEvent>();
        rollingPolicy.setContext(context);
        rollingPolicy.setParent(fileAppender);
        rollingPolicy.setFileNamePattern(logDir.getAbsolutePath() + "/wallet.%d{yyyy-MM-dd,UTC}.log.gz");
        rollingPolicy.setMaxHistory(7);
        rollingPolicy.start();

        fileAppender.setEncoder(filePattern);
        fileAppender.setRollingPolicy(rollingPolicy);
        fileAppender.start();

        final PatternLayoutEncoder logcatTagPattern = new PatternLayoutEncoder();
        logcatTagPattern.setContext(context);
        logcatTagPattern.setPattern("%logger{0}");
        logcatTagPattern.start();

        final PatternLayoutEncoder logcatPattern = new PatternLayoutEncoder();
        logcatPattern.setContext(context);
        logcatPattern.setPattern("[%thread] %msg%n");
        logcatPattern.start();

        final LogcatAppender logcatAppender = new LogcatAppender();
        logcatAppender.setContext(context);
        logcatAppender.setTagEncoder(logcatTagPattern);
        logcatAppender.setEncoder(logcatPattern);
        logcatAppender.start();

        final ch.qos.logback.classic.Logger log = context.getLogger(Logger.ROOT_LOGGER_NAME);
        log.addAppender(fileAppender);
        log.addAppender(logcatAppender);
        log.setLevel(Level.INFO);
    }


    public void onPairReceived(String requesteePubKey, final String name) {
        Intent intent = new Intent(BaseActivity.NOTIF_DIALOG_EVENT);
        intent.putExtra(INTENT_EXTRA_PROF_KEY,requesteePubKey);
        intent.putExtra(INTENT_EXTRA_PROF_NAME,name);
        broadcastManager.sendBroadcast(intent);

    }

    public void onPairResponseReceived(String requesteePubKey, String responseDetail) {
        PendingIntent pendingIntent = PendingIntent.getActivity(this,0,new Intent(this, HomeActivity.class),0);
        Notification not = new Notification.Builder(this)
                .setContentTitle("Pair acceptance received")
                .setContentText(responseDetail)
                .setSmallIcon(R.drawable.profile)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .build();
        notificationManager.notify(100,not);
    }

    public PackageInfo getPackageInfo() {
        return info;
    }

    public long getTimeCreateApplication() {
        return timeCreateApplication;
    }


    public void onConnect(final String profPubKey) {
        log.info("Profile connected");
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    profilesModule.addService(profPubKey,EnabledServices.CHAT.getName());
                }catch (Exception e){
                    e.printStackTrace();
                    log.error("Error adding chat service",e);
                }
            }
        }).start();
        // notify
        Intent intent = new Intent(INTENT_ACTION_PROFILE_CONNECTED);
        broadcastManager.sendBroadcast(intent);
    }

    public void onDisconnect() {
        Intent intent = new Intent(INTENT_ACTION_PROFILE_DISCONNECTED);
        broadcastManager.sendBroadcast(intent);
    }

    public void onCheckInFail(String detail) {
        log.info("onCheckInFail");
        Intent intent = new Intent(INTENT_ACTION_PROFILE_CHECK_IN_FAIL);
        intent.putExtra(INTENT_EXTRA_ERROR_DETAIL,detail);
        broadcastManager.sendBroadcast(intent);
    }


    public File getBackupDir(){
        return Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
    }

    @Override
    public void onTerminate() {
        super.onTerminate();
        unregisterReceiver(serviceReceiver);
    }

    public void cancelChatNotifications() {
        notificationManager.cancel(43);
    }

    public NotificationManager getNotificationManager() {
        return notificationManager;
    }

    public LocalBroadcastManager getBroadcastManager() {
        return broadcastManager;
    }

    public PairingModule getPairingModule() {
        return pairingModule;
    }

    public ChatModule getChatModule() {
        return chatModule;
    }

    public ProfilesModule getProfilesModule() {
        return profilesModule;
    }

    public String getSelectedProfilePubKey() {
        return selectedProfilePubKey;
    }

    public void setSelectedProfilePubKey(String selectedProfilePubKey) {
        appConf.setSelectedProfPubKey(selectedProfilePubKey);
        this.selectedProfilePubKey = selectedProfilePubKey;
    }
}
