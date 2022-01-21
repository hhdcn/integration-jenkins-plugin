package com.schbrain.ci.jenkins.plugins.integration.builder;

import com.schbrain.ci.jenkins.plugins.integration.builder.config.DeployToK8sConfig;
import com.schbrain.ci.jenkins.plugins.integration.builder.config.DockerConfig;
import com.schbrain.ci.jenkins.plugins.integration.builder.config.DockerConfig.PushConfig;
import com.schbrain.ci.jenkins.plugins.integration.builder.config.MavenConfig;
import com.schbrain.ci.jenkins.plugins.integration.builder.constants.Constants.DockerConstants;
import com.schbrain.ci.jenkins.plugins.integration.builder.util.FileUtils;
import com.schbrain.ci.jenkins.plugins.integration.builder.util.Logger;
import edu.umd.cs.findbugs.annotations.CheckForNull;
import hudson.EnvVars;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.BuildListener;
import hudson.model.Descriptor;
import hudson.tasks.Builder;
import net.sf.json.JSONObject;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest;
import org.springframework.lang.Nullable;

import java.io.IOException;

import static com.schbrain.ci.jenkins.plugins.integration.builder.util.FileUtils.lookupFile;

/**
 * @author liaozan
 * @since 2022/1/14
 */
public class IntegrationBuilder extends Builder {

    private final MavenConfig mavenConfig;
    private final DockerConfig dockerConfig;
    private final DeployToK8sConfig deployToK8sConfig;

    @DataBoundConstructor
    public IntegrationBuilder(@Nullable MavenConfig mavenConfig,
                              @Nullable DockerConfig dockerConfig,
                              @Nullable DeployToK8sConfig deployToK8sConfig) {
        this.mavenConfig = mavenConfig;
        this.dockerConfig = dockerConfig;
        this.deployToK8sConfig = deployToK8sConfig;
    }

    @Nullable
    public MavenConfig getMavenConfig() {
        return mavenConfig;
    }

    @Nullable
    public DockerConfig getDockerConfig() {
        return dockerConfig;
    }

    @Nullable
    public DeployToK8sConfig getDeployToK8sConfig() {
        return deployToK8sConfig;
    }

    /**
     * Builder start
     */
    @Override
    public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) throws IOException, InterruptedException {
        EnvVars envVars = new EnvVars(build.getBuildVariables());
        BuilderContext builderContext = new BuilderContext.Builder()
                .build(build)
                .launcher(launcher)
                .listener(listener)
                .logger(Logger.of(listener.getLogger()))
                .workspace(checkWorkspaceValid(build.getWorkspace()))
                .envVars(envVars)
                .build();
        this.doPerformBuild(builderContext);
        return true;
    }

    @Override
    public IntegrationDescriptor getDescriptor() {
        return (IntegrationDescriptor) super.getDescriptor();
    }

    protected void doPerformBuild(BuilderContext context) {
        try {
            // fail fast if workspace is invalid
            checkWorkspaceValid(context.getWorkspace());
            // maven build
            performMavenBuild(context);
            // read dockerInfo
            readDockerBuildInfo(context);
            // docker build
            performDockerBuild(context);
            // docker push
            performDockerPush(context);
            // prune images
            pruneImages(context);
            // delete the built image if possible
            deleteImageAfterBuild(context);
            // deploy
            deployToRemote(context);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Check workspace
     */
    private FilePath checkWorkspaceValid(@CheckForNull FilePath workspace) throws IOException, InterruptedException {
        if (workspace == null) {
            throw new IllegalStateException("workspace is null");
        }
        if (!workspace.exists()) {
            throw new IllegalStateException("workspace is not exist");
        }
        return workspace;
    }

    /**
     * Build project through maven
     */
    private void performMavenBuild(BuilderContext context) throws Exception {
        MavenConfig mavenConfig = getMavenConfig();
        if (mavenConfig == null) {
            context.log("maven build is not checked");
            return;
        }

        mavenConfig.build(context);
    }

    private void performDockerBuild(BuilderContext context) throws Exception {
        DockerConfig dockerConfig = getDockerConfig();
        if (dockerConfig == null) {
            context.log("docker build is not checked");
            return;
        }

        dockerConfig.build(context);
    }

    private void readDockerBuildInfo(BuilderContext context) throws IOException, InterruptedException {
        EnvVars envVars = context.getEnvVars();
        FilePath dockerBuildInfo = lookupFile(context, DockerConstants.BUILD_INFO_FILE_NAME);
        if (dockerBuildInfo == null) {
            context.log("%s file not exist, skip docker build", DockerConstants.BUILD_INFO_FILE_NAME);
            return;
        }
        // overwriting existing environment variables is not allowed
        FileUtils.filePathToMap(dockerBuildInfo).forEach(envVars::putIfAbsent);
    }

    private void performDockerPush(BuilderContext context) throws Exception {
        DockerConfig dockerConfig = getDockerConfig();
        if (dockerConfig == null) {
            context.log("docker build is not checked");
            return;
        }
        PushConfig pushConfig = dockerConfig.getPushConfig();
        if (pushConfig == null) {
            context.log("docker push is not checked");
            return;
        }

        pushConfig.build(context);
    }

    private void pruneImages(BuilderContext context) throws Exception {
        DockerConfig dockerConfig = getDockerConfig();
        if (dockerConfig == null) {
            context.log("docker build is not checked");
            return;
        }
        context.execute("docker image prune -f");
    }

    /**
     * Delete the image produced in the build
     */
    private void deleteImageAfterBuild(BuilderContext context) throws InterruptedException {
        EnvVars envVars = context.getEnvVars();
        DockerConfig dockerConfig = getDockerConfig();

        if (dockerConfig == null) {
            context.log("docker build is not checked");
            return;
        }
        if (!dockerConfig.getDeleteImageAfterBuild()) {
            context.log("delete built image is skip");
            return;
        }

        context.log("try to delete built image");
        String imageName = envVars.get("IMAGE_NAME");
        if (imageName == null) {
            return;
        }

        String command = String.format("docker rmi -f %s", imageName);
        context.execute(command);
    }

    /**
     * 部署镜像到远端
     */
    private void deployToRemote(BuilderContext context) throws Exception {
        DeployToK8sConfig k8sConfig = getDeployToK8sConfig();
        if (k8sConfig == null) {
            context.getLogger().println("k8s deploy is not checked");
            return;
        }

        k8sConfig.build(context);
    }

    // can not move outside builder class
    @Extension
    @SuppressWarnings({"unused"})
    public static class IntegrationDescriptor extends Descriptor<Builder> {

        public IntegrationDescriptor() {
            load();
        }

        @Override
        public String getDisplayName() {
            return "发布集成";
        }

        @Override
        public boolean configure(StaplerRequest req, JSONObject formData) throws FormException {
            save();
            return super.configure(req, formData);
        }

    }

}
