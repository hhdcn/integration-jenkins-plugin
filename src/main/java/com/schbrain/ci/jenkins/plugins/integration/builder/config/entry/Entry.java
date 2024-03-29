package com.schbrain.ci.jenkins.plugins.integration.builder.config.entry;

import hudson.model.AbstractDescribableImpl;

import java.util.Map;

/**
 * @author liaozan
 * @since 2022/1/16
 */
public abstract class Entry extends AbstractDescribableImpl<Entry> {

    public abstract void contribute(Map<String, String> options);

}
