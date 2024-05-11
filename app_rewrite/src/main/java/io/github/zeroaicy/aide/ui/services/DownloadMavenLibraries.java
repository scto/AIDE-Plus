package io.github.zeroaicy.aide.ui.services;

import android.app.Activity;
import android.text.TextUtils;
import com.aide.ui.App;
import com.aide.ui.services.MavenService;
import com.aide.ui.services.NativeCodeSupportService;
import com.aide.ui.services.NativeCodeSupportService$q;
import com.aide.ui.util.BuildGradle;
import com.aide.ui.util.MavenMetadataXml;
import com.aide.ui.util.PomXml;
import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.Iterator;

public class DownloadMavenLibraries implements Callable<Void> {


	private Runnable downloadCompleteCallback;
    private List<BuildGradle.MavenDependency> deps;
    private final List<BuildGradle.RemoteRepository> remoteRepositorys = new ArrayList<>();
    private final Activity activity;
    protected final NativeCodeSupportService downloadService;

	private static final BuildGradle.RemoteRepository defaultRemoteRepository = new BuildGradle.RemoteRepository(1, "https://maven.aliyun.com/repository/public");

    public DownloadMavenLibraries(NativeCodeSupportService downloadService, Activity activity, List<BuildGradle.MavenDependency> deps, List<BuildGradle.RemoteRepository> remoteRepositorys, Runnable completeCallback) {

		this.downloadService = downloadService;
		this.activity = activity;
		this.downloadCompleteCallback = completeCallback;
		this.deps = deps;

		deduplication(remoteRepositorys);
    }

	private void deduplication(List<BuildGradle.RemoteRepository> remoteRepositorys) {
		Set<BuildGradle.RemoteRepository> remoteRepositorySet = new HashSet<>();
		// 添加默认maven仓库
		remoteRepositorySet.add(defaultRemoteRepository);
		// 过滤重复maven仓库
		for (BuildGradle.RemoteRepository remoteRepository : remoteRepositorys) {
			if (remoteRepositorySet.contains(remoteRepository)) {
				// 过滤重复仓库
				continue;
			}
			// 标记
			remoteRepositorySet.add(remoteRepository);

			this.remoteRepositorys.add(remoteRepository);
		}
	}

    @Override
    public Void call() {
		//是否有已完成的下载
		boolean downloadComplete = false;
		int count = 0;
		for (BuildGradle.MavenDependency dep : this.deps) {
			try {
				//遍历远程仓库
				for (BuildGradle.RemoteRepository remoteRepository : this.remoteRepositorys) {
					String mavenMetadataUrl = MavenService.getMetadataUrl(remoteRepository, dep);
					String mavenMetadataPath = MavenService.getMetadataPath(remoteRepository, dep);
					try {
						StringBuilder sb = new StringBuilder();
						sb.append("metadata -> ");

						sb.append(dep.groupId);
						sb.append(":");
						sb.append(dep.artifactId);
						sb.append(":");
						sb.append(dep.version);

						String dependencyString = sb.toString();
						// 下载清单文件
						NativeCodeSupportService.Hw(this.downloadService, dependencyString, (count * 100) / this.deps.size(), 0);
						//已存在 长度不一致时更新
						NativeCodeSupportService.gn(this.downloadService, mavenMetadataUrl, mavenMetadataPath, false);
					}
					catch (Throwable unused) {
						// 仓库有问题
						continue;
					}

					// 判断是否存在
					if (!new File(mavenMetadataPath).exists()) {
						continue;
					}

					MavenMetadataXml metadataXml = new MavenMetadataXml().getConfiguration(mavenMetadataPath);
					//查看maven-metadata.xml是否下载成功
					String version = metadataXml.getVersion(dep.version);
					if (version == null) continue;

					// 更新依赖库版本
					dep.version = version;

					// 下载pom文件
					if (!downloadArtifactFile(remoteRepository, dep, version, ".pom", count)) {
						//仓库有问题，跳过
						continue;
					}

					String pomPath = MavenService.getArtifactPath(remoteRepository, dep, dep.version, ".pom");
					PomXml configuration = PomXml.empty.getConfiguration(pomPath);
					// pom中的 packaging 
					String curPackaging = configuration.getPackaging();

					// 父类依赖认为是 pom
					// 或当前pom 声明是pom
					if ("pom".equals(curPackaging)
						|| "pom".equals(dep.packaging)) {
						count++;
						downloadComplete = true;
						break;
					}
					// 更新type
					dep.packaging = curPackaging;
					// 从父依赖解析出来的，最为准确
					boolean isAttemptn = false;
					if (TextUtils.isEmpty(dep.packaging)) {
						// 启用尝试 下载aar模式
						isAttemptn = true;
						dep.packaging = "aar";
					}

					String artifactType = "." + dep.packaging;
					//下载
					if (downloadArtifactFile(remoteRepository, dep, version, artifactType, count)) {
						count++;
						downloadComplete = true;
						break;
					}

					if (isAttemptn) {
						dep.packaging = "jar";
						artifactType = "." + dep.packaging;
						if (downloadArtifactFile(remoteRepository, dep, version, artifactType, count)) {
							count++;
							downloadComplete = true;
							break;
						}
					}
				}
			}
			catch (Throwable e) {

			}
		}
		final boolean downloadComplete2 = downloadComplete;
		// 回调通知[下载完成]
		App.aj(new Runnable(){
				@Override
				public void run() {
					NativeCodeSupportService.FH(DownloadMavenLibraries.this.downloadService);
					if (downloadComplete2) {
						DownloadMavenLibraries.this.downloadCompleteCallback.run();
					}
				}
			});
		return null;
	}

	public boolean downloadArtifactFile(BuildGradle.RemoteRepository remoteRepository, BuildGradle.MavenDependency dependency, String version, String artifactType, int count) {

		String artifactUrl = MavenService.getArtifactUrl(remoteRepository, dependency, version, artifactType);
		String artifactPath = MavenService.getArtifactPath(remoteRepository, dependency, version, artifactType);

		File artifactFile = new File(artifactPath);
		if (artifactFile.exists()) {
			return true;
		}
		StringBuilder sb = new StringBuilder();
		sb.append(dependency.groupId);
		sb.append(":");
		sb.append(dependency.artifactId);
		sb.append(":");
		sb.append(version);
		sb.append("@");
		sb.append(dependency.packaging);

		String dependencyString = sb.toString();

		try {
			//通知下载进度
			NativeCodeSupportService.Hw(this.downloadService, dependencyString, (count * 100) / this.deps.size(), 0);
			//如果文件存在且长度一致则不下载
			NativeCodeSupportService.gn(this.downloadService, artifactUrl, artifactPath, true);
		}
		catch (Throwable unused) {
//				Log.d(" Maven Download", "dep", dependencyString);
//				Log.d(" Maven Download", Log.getStackTraceString(unused));					

			return false;
		}

		return artifactFile.exists();
	}


	/**
	 * 旧的实现
	 */
	public Void call3() {
        String buildMavenMetadataPath = null;
        String version;
        String aarPath;
        String jarUrl;
        String jarPath;
        String jarType = ".jar";
        String aarType = ".aar";
        String pomType = ".pom";
        try {

            ArrayList<BuildGradle.RemoteRepository> remoteRepositorys = new ArrayList<>();

            for (BuildGradle.RemoteRepository remoteRepository : this.remoteRepositorys) {
                if (!remoteRepositorys.contains(remoteRepository)) {
                    remoteRepositorys.add(remoteRepository);
                }
            }

            Iterator<BuildGradle.MavenDependency> it = this.deps.iterator();
            boolean z2 = false;
            int i = 0;
            while (it.hasNext()) {

                if (Thread.interrupted()) {
					throw new InterruptedException();
				}

				BuildGradle.MavenDependency dependency = it.next();
				for (BuildGradle.RemoteRepository remoteRepository : remoteRepositorys) {
					try {
						String buildMavenMetadataUrl = MavenService.getMetadataUrl(remoteRepository, dependency);
						buildMavenMetadataPath = MavenService.getMetadataPath(remoteRepository, dependency);
						//下载
						NativeCodeSupportService.gn(this.downloadService, buildMavenMetadataUrl, buildMavenMetadataPath, false);
					}
					catch (Exception unused) {
					}
					// aM url
					// XL path
					if (new File(buildMavenMetadataPath).exists() && (version = new MavenMetadataXml().getConfiguration(buildMavenMetadataPath).getVersion(dependency.version)) != null) {
						String pomUrl = MavenService.getArtifactUrl(remoteRepository, dependency, version, pomType);
						String pomPath = MavenService.getArtifactPath(remoteRepository, dependency, version, pomType);

						String aarUrl = MavenService.getArtifactUrl(remoteRepository, dependency, version, aarType);
						aarPath = MavenService.getArtifactPath(remoteRepository, dependency, version, aarType);

						jarUrl = MavenService.getArtifactUrl(remoteRepository, dependency, version, jarType);
						jarPath = MavenService.getArtifactPath(remoteRepository, dependency, version, jarType);


						if ((!new File(jarPath).exists() 
							&& !new File(aarPath).exists()) 

							|| !new File(pomPath).exists()) {

							StringBuilder sb = new StringBuilder();
							sb.append(dependency.groupId);
							sb.append(":");
							sb.append(dependency.artifactId);
							sb.append(":");
							sb.append(version);
							String dependencyString = sb.toString();

							//通知下载进度
							NativeCodeSupportService.Hw(this.downloadService, dependencyString, (i * 100) / this.deps.size(), 0);
							//下载 复用已有下载[长度一致]
							NativeCodeSupportService.gn(this.downloadService, pomUrl, pomPath, true);
							NativeCodeSupportService.gn(this.downloadService, aarUrl, aarPath, true);
							NativeCodeSupportService.gn(this.downloadService, jarUrl, jarPath, true);


							i++;
							if ((new File(jarPath).exists() 
								|| new File(aarPath).exists()) 
								&& new File(pomPath).exists()) {
								z2 = true;
								break;
							}
						}
					}
				}
            }

			final boolean downloadComplete2 = z2;
            App.aj(new Runnable(){
					@Override
					public void run() {
						NativeCodeSupportService.FH(downloadService);
						if (downloadComplete2) {
							downloadCompleteCallback.run();
						}
					}
				});
            return null;
        }
		catch (Throwable th) {
            throw new Error(th);
        }
    }
}