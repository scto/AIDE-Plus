package io.github.zeroaicy;

import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.os.StrictMode;
import com.aide.common.AppLog;
import com.aide.ui.AIDEApplication;
import com.aide.ui.ServiceContainer;
import io.github.zeroaicy.aide.highlight.CodeTheme;
import io.github.zeroaicy.aide.preference.ZeroAicySetting;
import io.github.zeroaicy.aide.shizuku.ShizukuUtil;
import io.github.zeroaicy.aide.utils.Logger;
import io.github.zeroaicy.aide.utils.jks.JksKeyStore;
import io.github.zeroaicy.util.ContextUtil;
import io.github.zeroaicy.util.DebugUtil;
import io.github.zeroaicy.util.FileUtil;
import io.github.zeroaicy.util.Log;
import io.github.zeroaicy.util.crash.CrashApphandler;
import io.github.zeroaicy.util.reflect.ReflectPie;
import java.io.FileOutputStream;
import java.io.PrintStream;

public class ZeroAicyAIDEApplication extends AIDEApplication {

	private static final String TAG = "ZeroAicyAIDEApplication ";

	public static final long now = System.currentTimeMillis();
	public static final boolean reflectAll = ReflectPie.reflectAll();

	static{
		// 防止各种闪退，默认写入在数据目录2.
		DebugUtil.debug();
	}

	@Override
	public void onCreate() {
		super.onCreate();

		// 更改日志路径
		DebugUtil.debug(this, false);

		String crashProcessName = getPackageName() + ":crash";
		
		String curProcessName = ContextUtil.getProcessName();

		if (crashProcessName.equals(curProcessName) 
			|| curProcessName.contains("crash")) {
			AppLog.d(TAG, "crash进程: ", curProcessName);
			return;
		}

		//解除反射
		AppLog.d(TAG, "解除反射: " + reflectAll);
		
		// 异常显示Activity
		CrashApphandler.getInstance().onCreated();

		//初始化ZeroAicy设置
		ZeroAicySetting.init(this);
		// 自定义CodeTheme初始化
		CodeTheme.init(this);
		//初始化Shizuku库
		ShizukuUtil.initialized(this);

		// 更新加密库
		JksKeyStore.initBouncyCastleProvider();

		// 防止App的context为null
		ServiceContainer.sh(this);

		// 判断是否显示AIDE-WhatsNewDialog
		if (ZeroAicySetting.isReinstall()) {
			SharedPreferences sharedPreferences = getSharedPreferences("WhatsNew", 0);
			SharedPreferences.Editor edit = sharedPreferences.edit();
			// 重置
			edit.putInt("ShownVersion", 0).apply();
		}

		// Return if this application is not in debug mode 
		AppLog.d(TAG, "Application初始化耗时: " + (System.currentTimeMillis() - now) + "ms");
	}


	// 共存版发送logcat log
	private void method2() {
		if (ContextUtil.isMainProcess()) {
			if ("io.github.zeroaicy.aide".equals(getPackageName())) {
				// 
				Logger.onContext(this, "io.github.zeroaicy.aide1");	
			} else {
				Logger.onContext(this, "io.github.zeroaicy.aide");	
			}
			// 附加AndroidLog
			Logger logger = Logger.getLogger();
			// 会向ZeroAicyLog打印
			logger.addDefaultLogListener();
			logger.start();
		}

		/*
		 StrictMode.setThreadPolicy(
		 new StrictMode.ThreadPolicy.Builder()
		 //.detectDiskReads()  // 监测读磁盘
		 //.detectDiskWrites()  // 监测写磁盘
		 .detectNetwork()      // 监测网络操作
		 .detectCustomSlowCalls()  // 监测哪些方法执行慢
		 .detectResourceMismatches()  // 监测资源不匹配
		 .penaltyLog()   // 打印日志，也可设置为弹窗提示penaltyDialog()或者直接使进程死亡penaltyDeath()
		 .build());

		 // 监测VM虚拟机进程级别的Activity泄漏或者其它资源泄漏
		 StrictMode.setVmPolicy(new StrictMode.VmPolicy.Builder()
		 .detectActivityLeaks()  // 监测内存泄露情况
		 .detectLeakedSqlLiteObjects()  // SqlLite资源未关闭，如cursor
		 .detectLeakedClosableObjects()  // Closable资源未关闭，如文件流
		 .detectCleartextNetwork()  // 监测明文网络
		 //.setClassInstanceLimit(MyClass.class, 1)  // 设置某个类的实例上限，可用于内存泄露提示
		 .detectLeakedRegistrationObjects()  // 监测广播或者ServiceConnection是否有解注册
		 .penaltyLog()
		 .build());
		 //*/
	}


	/**
	 * 严苛模式
	 */
	private void method() {
		ApplicationInfo appInfo = getApplicationInfo(); 
		int appFlags = appInfo.flags; 

		if (false && ContextUtil.isMainProcess() && (appFlags & ApplicationInfo.FLAG_DEBUGGABLE) != 0) {     
			Log.d(TAG, "启用严苛模式: ");
			// 监测当前线程（UI线程）上的网络、磁盘读写等耗时操作
			StrictMode.setThreadPolicy(
				new StrictMode.ThreadPolicy.Builder()
				//.detectDiskReads()  // 监测读磁盘
				//.detectDiskWrites()  // 监测写磁盘
				.detectNetwork()      // 监测网络操作
				.detectCustomSlowCalls()  // 监测哪些方法执行慢
				.detectResourceMismatches()  // 监测资源不匹配
				.penaltyLog()   // 打印日志，也可设置为弹窗提示penaltyDialog()或者直接使进程死亡penaltyDeath()
				.penaltyDialog()
				.penaltyDeath()
				.penaltyDropBox()  //监测到将信息存到Dropbox文件夹 data/system/dropbox
				.build());

			// 监测VM虚拟机进程级别的Activity泄漏或者其它资源泄漏
			StrictMode.setVmPolicy(
				new StrictMode.VmPolicy.Builder()
				.detectActivityLeaks()  // 监测内存泄露情况
				.detectLeakedSqlLiteObjects()  // SqlLite资源未关闭，如cursor
				.detectLeakedClosableObjects()  // Closable资源未关闭，如文件流
				.detectCleartextNetwork()  // 监测明文网络
				//.setClassInstanceLimit(MyClass.class, 1)  // 设置某个类的实例上限，可用于内存泄露提示
				.detectLeakedRegistrationObjects()  // 监测广播或者ServiceConnection是否有解注册
				.penaltyLog()
				.build());
		}
	}



	/**
	 * 当日志系统崩溃时,，进行修复，以便测试
	 * 此实现依赖反射，使用时注意检查
	 */
	private void testLog() {

		boolean log = Log.getLog() == null;

		if (log) {
			Log.AsyncOutputStreamHold logHold = Log.getLogHold();
			String logCatPath = FileUtil.LogCatPath + "_test.txt";

			if (logHold == null) {
				AppLog.d(TAG, "LogHold 为 null");
				logHold = new Log.AsyncOutputStreamHold(logCatPath);
				ReflectPie.on(Log.class).set("mLogHold", logHold);
			}
			if (log = Log.getLog() == null) {
				AppLog.d(TAG, "LogHold mLog null");
				FileOutputStream createOutStream = Log.AsyncOutputStreamHold.createOutStream(logCatPath);
				Log.AsyncOutputStreamHold.AsyncOutStream asyncOutStream = new Log.AsyncOutputStreamHold.AsyncOutStream(createOutStream);
				PrintStream mLog = new PrintStream(asyncOutStream);
				ReflectPie.on(logHold).set("mLog", mLog);

			}
			ReflectPie.on(Log.class).call("printPreMsgList");
		}
	}
}
