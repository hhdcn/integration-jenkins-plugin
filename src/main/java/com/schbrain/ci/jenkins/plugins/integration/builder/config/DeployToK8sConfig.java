package com.schbrain.ci.jenkins.plugins.integration.builder.config;

import com.schbrain.ci.jenkins.plugins.integration.builder.config.entry.Entry;
import hudson.Extension;
import hudson.Util;
import hudson.model.Descriptor;
import org.kohsuke.stapler.DataBoundConstructor;

import java.util.List;

/**
 * @author liaozan
 * @since 2022/1/16
 */
@SuppressWarnings("unused")
public class DeployToK8sConfig extends BuildConfig<DeployToK8sConfig> {

    private final List<Entry> entries;

    private final String configLocation;

    private final String deployFileLocation;

    @DataBoundConstructor
    public DeployToK8sConfig(List<Entry> entries, String configLocation, String deployFileLocation) {
        this.entries = Util.fixNull(entries);
        this.configLocation = Util.fixNull(configLocation);
        this.deployFileLocation = Util.fixNull(deployFileLocation);
    }

    public List<Entry> getEntries() {
        return entries;
    }

    public String getConfigLocation() {
        return configLocation;
    }

    public String getDeployFileLocation() {
        return deployFileLocation;
    }

    @Extension
    public static class DescriptorImpl extends Descriptor<DeployToK8sConfig> {

    }

}
