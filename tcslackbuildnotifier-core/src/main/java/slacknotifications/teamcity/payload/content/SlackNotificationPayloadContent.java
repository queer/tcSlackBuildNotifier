package slacknotifications.teamcity.payload.content;

import jetbrains.buildServer.Build;
import jetbrains.buildServer.BuildProblemData;
import jetbrains.buildServer.RootUrlHolder;
import jetbrains.buildServer.serverSide.*;
import jetbrains.buildServer.tests.TestInfo;
import jetbrains.buildServer.users.SUser;
import jetbrains.buildServer.vcs.SVcsModification;
import slacknotifications.teamcity.BuildStateEnum;
import slacknotifications.teamcity.Loggers;
import slacknotifications.teamcity.SlackNotificator;
import slacknotifications.teamcity.TeamCityIdResolver;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;

@SuppressWarnings({"JavaDoc", "unused", "ConstantConditions", "WeakerAccess", "CollectionDeclaredAsConcreteClass", "PackageVisibleField"})
public class SlackNotificationPayloadContent {
    public static final String BUILD_STATUS_FAILURE = "Failed";
    public static final String BUILD_STATUS_SUCCESS = "Succeeded";
    public static final String BUILD_STATUS_RUNNING = "Running";
    public static final String BUILD_STATUS_NO_CHANGE = "Unchanged";
    public static final String BUILD_STATUS_FIXED = "Fixed";
    public static final String BUILD_STATUS_BROKEN = "Broken";
    public static final String BUILD_STATUS_UNKNOWN = "Unknown";
    String buildStatus;
    String buildResult;
    String buildResultPrevious;
    String buildResultDelta;
    String notifyType;
    String buildFullName;
    String buildName;
    String buildId;
    String buildTypeId;
    String buildInternalTypeId;
    String buildExternalTypeId;
    String buildStatusUrl;
    String buildStatusHtml;
    String rootUrl;
    String projectName;
    String projectId;
    String projectInternalId;
    String projectExternalId;
    String buildNumber;
    String agentName;
    String agentOs;
    String agentHostname;
    String triggeredBy;
    String comment;
    String message;
    String text;
    String branchName;
    
    /*
        public final static String BUILD_STATUS_FAILURE   = "failure";
        public final static String BUILD_STATUS_SUCCESS   = "success";
        public final static String BUILD_STATUS_RUNNING   = "running";
        public final static String BUILD_STATUS_NO_CHANGE = "unchanged";
        public final static String BUILD_STATUS_FIXED     = "fixed";
        public final static String BUILD_STATUS_BROKEN    = "broken";
        public final static String BUILD_STATUS_UNKNOWN   = "unknown";*/
    String branchDisplayName;
    String buildStateDescription;
    String progressSummary;
    List<Commit> commits;
    Boolean branchIsDefault;
    Branch branch;
    private boolean isFirstFailedBuild;
    private String buildLink;
    private String color;
    private long elapsedTime;
    private boolean isComplete;
    private ArrayList<String> failedBuildMessages = new ArrayList<String>();
    private ArrayList<String> failedTestNames = new ArrayList<String>();
    
    public SlackNotificationPayloadContent() {
    
    }
    
    /**
     * Constructor: Only called by RepsonsibilityChanged.
     *
     * @param server
     * @param buildType
     * @param buildState
     */
    public SlackNotificationPayloadContent(final RootUrlHolder server, final SBuildType buildType, final BuildStateEnum buildState) {
        populateCommonContent(server, buildType, buildState);
    }
    
    /**
     * Constructor: Called by everything except RepsonsibilityChanged.
     *
     * @param server
     * @param sRunningBuild
     * @param previousBuild
     * @param buildState
     */
    public SlackNotificationPayloadContent(final RootUrlHolder server, final SRunningBuild sRunningBuild, final Build previousBuild,
                                           final BuildStateEnum buildState) {
        
        commits = new ArrayList<Commit>();
        populateCommonContent(server, sRunningBuild, previousBuild, buildState);
        populateMessageAndText(sRunningBuild, buildState);
        populateCommits(sRunningBuild);
        populateArtifacts(sRunningBuild);
        populateResults(sRunningBuild);
    }
    
    private void populateResults(final SBuild sRunningBuild) {
        final List<BuildProblemData> failureReasons = sRunningBuild.getFailureReasons();
        final HashSet<String> failureTestNames = new HashSet<String>();
        final HashSet<String> failureMessages = new HashSet<String>();
        if(failureReasons == null) {
            return;
        }
        for(final BuildProblemData reason : failureReasons) {
            if(reason.getType().equals(BuildProblemData.TC_FAILED_TESTS_TYPE)) {
                final List<TestInfo> failedTestMessages = sRunningBuild.getTestMessages(0, 2000);
                if(!failedTestMessages.isEmpty()) {
                    for(final TestInfo failedTest : failedTestMessages) {
                        failureTestNames.add(failedTest.getName());
                    }
                } else {
                    failureMessages.add(reason.getDescription());
                }
            } else {
                failureMessages.add(reason.getDescription());
            }
        }
        failedBuildMessages = new ArrayList<String>(failureMessages);
        failedTestNames = new ArrayList<String>(failureTestNames);
    }
    
    @SuppressWarnings("TypeMayBeWeakened")
    private void populateCommits(final SRunningBuild sRunningBuild) {
        final List<SVcsModification> changes = sRunningBuild.getContainingChanges();
        if(changes == null) {
            return;
        }
        for(final SVcsModification change : changes) {
            final Collection<SUser> committers = change.getCommitters();
            String slackUserName = null;
            if(!committers.isEmpty()) {
                final SUser committer = committers.iterator().next();
                slackUserName = committer.getPropertyValue(SlackNotificator.USERNAME_KEY);
                Loggers.ACTIVITIES.debug("Resolved committer " + change.getUserName() + " to Slack User " + slackUserName);
            }
            commits.add(new Commit(change.getVersion(), change.getDescription(), change.getUserName(), slackUserName));
        }
    }
    
    private void populateArtifacts(final SRunningBuild runningBuild) {
        //ArtifactsInfo artInfo = new ArtifactsInfo(runningBuild);
        //artInfo.
        
    }
    
    /**
     * Used by RepsonsiblityChanged.
     * Therefore, does not have access to a specific build instance.
     *
     * @param server
     * @param buildType
     * @param state
     */
    private void populateCommonContent(final RootUrlHolder server, final SBuildType buildType, final BuildStateEnum state) {
        setBuildFullName(buildType.getFullName());
        setBuildName(buildType.getName());
        setBuildTypeId(TeamCityIdResolver.getBuildTypeId(buildType));
        setBuildStatusUrl(server.getRootUrl().replaceAll(":80", "") + "/viewLog.html?buildTypeId=" + buildType.getBuildTypeId() + "&buildId=lastFinished");
    }
    
    private void populateMessageAndText(final SRunningBuild sRunningBuild,
                                        final BuildStateEnum state) {
        // Message is a long form message, for on webpages or in email.
        
        // Text is designed to be shorter, for use in Text messages and the like.
        setText(getBuildDescriptionWithLinkSyntax()
                + " has " + state.getDescriptionSuffix() + ". Status: " + buildResult);
    }
    
    /**
     * Used by everything except ResponsibilityChanged. Is passed a valid build instance.
     *
     * @param server
     * @param sRunningBuild
     * @param previousBuild
     * @param buildState
     */
    private void populateCommonContent(final RootUrlHolder server, final SRunningBuild sRunningBuild, final Build previousBuild,
                                       final BuildStateEnum buildState) {
        setBuildResult(sRunningBuild, previousBuild, buildState);
        setBuildFullName(sRunningBuild.getBuildType().getFullName());
        setBuildName(sRunningBuild.getBuildType().getName());
        setBuildId(Long.toString(sRunningBuild.getBuildId()));
        setBuildTypeId(TeamCityIdResolver.getBuildTypeId(sRunningBuild.getBuildType()));
        setAgentName(sRunningBuild.getAgentName());
        setElapsedTime(sRunningBuild.getElapsedTime());
        
        try {
            if(sRunningBuild.getBranch() != null) {
                setBranch(sRunningBuild.getBranch());
                setBranchDisplayName(getBranch().getDisplayName());
                setBranchIsDefault(getBranch().isDefaultBranch());
            } else {
                Loggers.SERVER.debug("SlackNotificationPayloadContent :: Branch is null. Either feature branch support is not configured or Teamcity does not support feature branches on this VCS");
            }
        } catch(final NoSuchMethodError e) {
            Loggers.SERVER.debug("SlackNotificationPayloadContent :: Could not get Branch Info by calling sRunningBuild.getBranch(). Probably an old version of TeamCity");
        }
        final String r = server.getRootUrl() == null ? "" : server.getRootUrl();
        setBuildStatusUrl(r.replaceAll(":80", "") + "/viewLog.html?buildTypeId=" + getBuildTypeId() + "&buildId=" + getBuildId());
        final String branchSuffix = getBranchIsDefault() != null && getBranchIsDefault() || getBranchDisplayName() == null ? "" : " [" + getBranchDisplayName() + ']';
        setBuildDescriptionWithLinkSyntax('<' + getBuildStatusUrl() + '|' + getBuildResult() + " - " + sRunningBuild.getBuildType().getFullName() + " #" + sRunningBuild.getBuildNumber() + branchSuffix + '>');
    }
    
    private Branch getBranch() {
        return branch;
    }
    
    public void setBranch(final Branch branch) {
        this.branch = branch;
    }
    
    public String getBranchDisplayName() {
        return branchDisplayName;
    }
    
    public void setBranchDisplayName(final String displayName) {
        branchDisplayName = displayName;
    }
    
    public Boolean getBranchIsDefault() {
        return branchIsDefault;
    }
    
    public void setBranchIsDefault(final boolean branchIsDefault) {
        this.branchIsDefault = branchIsDefault;
    }
    
    public Boolean isMergeBranch() {
        return branchName != null && branchName.endsWith("/merge");
    }
    
    /**
     * Determines a useful build result. The one from TeamCity can't be trusted because it
     * is not set until all the Notifiers have run, of which we are one.
     *
     * @param sRunningBuild
     * @param previousBuild
     * @param buildState
     */
    private void setBuildResult(final Build sRunningBuild,
                                final Build previousBuild, final BuildStateEnum buildState) {
        
        if(previousBuild != null) {
            if(previousBuild.isFinished()) {
                if(previousBuild.getStatusDescriptor().isSuccessful()) {
                    buildResultPrevious = BUILD_STATUS_SUCCESS;
                } else {
                    buildResultPrevious = BUILD_STATUS_FAILURE;
                }
            } else {
                buildResultPrevious = BUILD_STATUS_RUNNING;
            }
        } else {
            buildResultPrevious = BUILD_STATUS_UNKNOWN;
        }
        
        isComplete = buildState == BuildStateEnum.BUILD_FINISHED;
        
        if(buildState == BuildStateEnum.BEFORE_BUILD_FINISHED || buildState == BuildStateEnum.BUILD_FINISHED) {
            if(sRunningBuild.getStatusDescriptor().isSuccessful()) {
                buildResult = BUILD_STATUS_SUCCESS;
                color = "good";
                if(buildResultPrevious.equals(buildResult)) {
                    buildResultDelta = BUILD_STATUS_NO_CHANGE;
                } else {
                    buildResultDelta = BUILD_STATUS_FIXED;
                }
            } else {
                buildResult = BUILD_STATUS_FAILURE;
                color = "danger";
                if(buildResultPrevious.equals(buildResult)) {
                    buildResultDelta = BUILD_STATUS_NO_CHANGE;
                } else {
                    buildResultDelta = BUILD_STATUS_BROKEN;
                    setFirstFailedBuild(true);
                }
            }
        } else {
            buildResult = BUILD_STATUS_RUNNING;
            buildResultDelta = BUILD_STATUS_UNKNOWN;
        }
    }
    
    // Getters and setters
    
    public String getBuildResult() {
        return buildResult;
    }
    
    public void setBuildResult(final String buildResult) {
        this.buildResult = buildResult;
    }
    
    public String getBuildFullName() {
        return buildFullName;
    }
    
    public void setBuildFullName(final String buildFullName) {
        this.buildFullName = buildFullName;
    }
    
    public String getBuildName() {
        return buildName;
    }
    
    public void setBuildName(final String buildName) {
        this.buildName = buildName;
    }
    
    public String getBuildId() {
        return buildId;
    }
    
    public void setBuildId(final String buildId) {
        this.buildId = buildId;
    }
    
    public String getBuildTypeId() {
        return buildTypeId;
    }
    
    public void setBuildTypeId(final String buildTypeId) {
        this.buildTypeId = buildTypeId;
    }
    
    public String getAgentName() {
        return agentName;
    }
    
    public void setAgentName(final String agentName) {
        this.agentName = agentName;
    }
    
    public String getBuildStatusUrl() {
        return buildStatusUrl;
    }
    
    public void setBuildStatusUrl(final String buildStatusUrl) {
        this.buildStatusUrl = buildStatusUrl;
    }
    
    public String getText() {
        return text;
    }
    
    public void setText(final String text) {
        this.text = text;
    }
    
    public String getBuildDescriptionWithLinkSyntax() {
        return buildLink;
    }
    
    public void setBuildDescriptionWithLinkSyntax(final String buildLink) {
        this.buildLink = buildLink;
    }
    
    public String getColor() {
        return color;
    }
    
    public void setColor(final String color) {
        this.color = color;
    }
    
    public long getElapsedTime() {
        return elapsedTime;
    }
    
    public void setElapsedTime(final long elapsedTime) {
        this.elapsedTime = elapsedTime;
    }
    
    public List<Commit> getCommits() {
        return commits;
    }
    
    public void setCommits(final List<Commit> commits) {
        this.commits = commits;
    }
    
    public boolean getIsComplete() {
        return isComplete;
    }
    
    public void setIsComplete(final boolean isComplete) {
        this.isComplete = isComplete;
    }
    
    public boolean getIsFirstFailedBuild() {
        return isFirstFailedBuild;
    }
    
    public void setFirstFailedBuild(final boolean isFirstFailedBuild) {
        this.isFirstFailedBuild = isFirstFailedBuild;
    }
    
    public ArrayList<String> getFailedBuildMessages() {
        return failedBuildMessages;
    }
    
    public ArrayList<String> getFailedTestNames() {
        return failedTestNames;
    }
}