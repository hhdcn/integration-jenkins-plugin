package com.schbrain.ci.jenkins.plugins.integration.builder.config.deploy;

import com.schbrain.ci.jenkins.plugins.integration.builder.BuilderContext;
import com.schbrain.ci.jenkins.plugins.integration.builder.config.entry.Entry;
import com.schbrain.ci.jenkins.plugins.integration.builder.constants.Constants.DeployConstants;
import com.schbrain.ci.jenkins.plugins.integration.builder.util.TemplateUtils;
import hudson.EnvVars;
import hudson.Extension;
import hudson.FilePath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.springframework.util.CollectionUtils;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import static com.schbrain.ci.jenkins.plugins.integration.builder.util.FileUtils.lookupFile;

/**
 * @author zhangdd on 2022/1/20
 */
public class DeployTemplateComponent extends DeployStyleRadio {

    private final String namespace;
    private final String replicas;
    private final String port;

    @DataBoundConstructor
    public DeployTemplateComponent(String namespace, String replicas, String port) {
        this.namespace = namespace;
        this.replicas = replicas;
        this.port = port;
    }

    public String getNamespace() {
        return namespace;
    }

    public String getReplicas() {
        return replicas;
    }

    public String getPort() {
        return port;
    }

    @Override
    public String getDeployFileLocation(BuilderContext context, List<Entry> entries) throws Exception {
        Path templatePath = getDeployTemplate(context);
        String deployFileLocation = new File(templatePath.getParent().toString(), DeployConstants.DEPLOY_FILE_NAME).getPath();
        resolveDeployFilePlaceholder(entries, templatePath.getFileName().toString(), deployFileLocation, context);
        return deployFileLocation;
    }

    private Path getDeployTemplate(BuilderContext context) throws Exception {
        FilePath existDeployTemplate = lookupFile(context, DeployConstants.TEMPLATE_FILE_NAME);
        if (null != existDeployTemplate) {
            existDeployTemplate.delete();
        }
        return Paths.get(context.getEnvVars().get("BUILD_SCRIPT"), DeployConstants.TEMPLATE_FILE_NAME);
    }

    private void resolveDeployFilePlaceholder(List<Entry> entries, String templateFileName,
                                              String deployFileLocation, BuilderContext context) throws Exception {
        EnvVars envVars = context.getEnvVars();
        envVars.put("NAMESPACE", getNamespace());
        envVars.put("PORT", getPort());
        envVars.put("REPLICAS", getReplicas());

        if (!CollectionUtils.isEmpty(entries)) {
            for (Entry entry : entries) {
                entry.contribute(envVars);
            }
        }

        FilePath templateFile = lookupFile(context, templateFileName);
        if (templateFile == null) {
            return;
        }

        String data = TemplateUtils.format(templateFile.readToString(), envVars);
        context.getLogger().println("resolved k8sDeployFile :\n" + data, false);
        Path resolvedLocation = Paths.get(deployFileLocation);
        if (Files.notExists(resolvedLocation)) {
            Files.createFile(resolvedLocation);
        }
        Files.write(resolvedLocation, data.getBytes(StandardCharsets.UTF_8));
    }

    @Extension
    public static class DescriptorImpl extends InventoryDescriptor {

        @Override
        public String getDisplayName() {
            return "使用默认模版";
        }

    }

}
