/*
 * The MIT License
 *
 * Copyright (c) 2017, CloudBees, Inc.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package org.jenkinsci.plugins.github_pr_label_filter;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.console.HyperlinkNote;
import hudson.util.FormValidation;
import jenkins.scm.api.SCMHead;
import jenkins.scm.api.SCMHeadCategory;
import jenkins.scm.api.SCMSource;
import jenkins.scm.api.trait.SCMHeadFilter;
import jenkins.scm.api.trait.SCMSourceContext;
import jenkins.scm.api.trait.SCMSourceRequest;
import jenkins.scm.api.trait.SCMSourceTrait;
import jenkins.scm.api.trait.SCMSourceTraitDescriptor;
import jenkins.scm.impl.ChangeRequestSCMHeadCategory;
import jenkins.scm.impl.trait.Discovery;
import org.jenkinsci.plugins.github_branch_source.GitHubSCMSource;
import org.jenkinsci.plugins.github_branch_source.GitHubSCMSourceContext;
import org.jenkinsci.plugins.github_branch_source.GitHubSCMSourceRequest;
import org.jenkinsci.plugins.github_branch_source.PullRequestSCMHead;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.github.GHLabel;
import org.kohsuke.github.GHPullRequest;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;

import java.io.IOException;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * A {@link Discovery} trait for GitHub that will only select pull requests that have specified label.
 *
 */
public class PullRequestLabelFilterTrait extends SCMSourceTrait {

    private static final String DEFAULT_MATCH_ALL_REGEX = ".*";

    /**
     * The regex for filtering pull request labels.
     */
    private String regex;

    /**
     * The pattern compiled from supplied regex
     */
    private transient Pattern pattern;

    /**
     * Constructor for stapler.
     *
     * @param regex Label for filtering pull request labels
     */
    @DataBoundConstructor
    public PullRequestLabelFilterTrait(String regex) {
        this.regex = regex;
        pattern = Pattern.compile(regex);
    }

    /**
     * Gets the pull request filter regex
     *
     * @return the pull request filter regex labels
     */
    public String getRegex() {
        return regex;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void decorateContext(SCMSourceContext<?, ?> context) {
        GitHubSCMSourceContext ctx = (GitHubSCMSourceContext) context;
        ctx.withFilter(getScmHeadFilter());
    }

    private SCMHeadFilter getScmHeadFilter() {
        SCMHeadFilter scmHeadFilter = new SCMHeadFilter() {

            @Override
            public boolean isExcluded(@NonNull SCMSourceRequest request,
                                      @NonNull SCMHead head) throws IOException, InterruptedException {

                if (request instanceof GitHubSCMSourceRequest && head instanceof PullRequestSCMHead) {
                    GitHubSCMSourceRequest githubRequest = (GitHubSCMSourceRequest) request;
                    PullRequestSCMHead pullRequestSCMHead = (PullRequestSCMHead) head;

                    if (!DEFAULT_MATCH_ALL_REGEX.equals(getRegex())) {
                        Iterable<GHPullRequest> ghPullRequests = githubRequest.getPullRequests();
                        for (GHPullRequest ghPullRequest : ghPullRequests) {
                            if (ghPullRequest.getNumber() == pullRequestSCMHead.getNumber()) {
                                boolean foundLabel = false;
                                StringBuilder allLabels = new StringBuilder();
                                for (GHLabel label : ghPullRequest.getLabels()) {
                                    allLabels.append(label.getName()).append(" ,");
                                    if (pattern.matcher(label.getName()).matches()) {
                                        foundLabel = true;
                                        request.listener().getLogger().format("%n    Will Build PR %s. Found matching label : %s%n", HyperlinkNote.encodeTo(ghPullRequest.getHtmlUrl().toString(), "#" + ghPullRequest.getNumber()), label.getName());
                                        break;
                                    }
                                }

                                if (!foundLabel) {
                                    request.listener().getLogger().format("%n    Won't build PR %s. No matching label found. Labels Found : %s%n", HyperlinkNote.encodeTo(ghPullRequest.getHtmlUrl().toString(), "#" + ghPullRequest.getNumber()), allLabels.toString());
                                }
                                return !foundLabel;
                            }
                        }
                    }
                }

                return false;
            }
        };

        return scmHeadFilter;
    }


    /**
     * {@inheritDoc}
     */
    @Override
    public boolean includeCategory(@NonNull SCMHeadCategory category) {
        return category instanceof ChangeRequestSCMHeadCategory;
    }

    @Extension
    @Discovery
    public static class DescriptorImpl extends SCMSourceTraitDescriptor {

        /**
         * {@inheritDoc}
         */
        @Override
        public String getDisplayName() {
            return "Filter pull requests by label";
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public Class<? extends SCMSourceContext> getContextClass() {
            return GitHubSCMSourceContext.class;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public Class<? extends SCMSource> getSourceClass() {
            return GitHubSCMSource.class;
        }

        @Restricted(NoExternalUse.class)
        public FormValidation doCheckRegex(@QueryParameter String value) {

            FormValidation formValidation;
            try {
                if (value.trim().isEmpty()) {
                    formValidation = FormValidation.error("Cannot have empty or blank regex.");
                } else {
                    Pattern.compile(value);
                    formValidation = FormValidation.ok();
                }

                if (DEFAULT_MATCH_ALL_REGEX.equals(value)) {
                    formValidation = FormValidation.warning("You should delete this trait instead of matching all labels");
                }

            } catch (PatternSyntaxException e) {
                formValidation = FormValidation.error("Invalid Regex : " + e.getMessage());
            }

            return formValidation;
        }
    }

}
