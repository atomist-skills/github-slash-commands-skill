# `atomist/git-chatops-skill`
# GitHub Slash Commands

<!---atomist-skill-readme:start--->

Run slash commands from commit messages and comments.

# What it's useful for

When you know that you want to trigger a command when committing code or commenting on a pull request or issue, just include the slash command.

* Create a pull request directly from a commit message, no separate action needed to create the PR
* Comment on an open pull request from a commit message
* Add labels to an issue or pull request when commenting
* Notify a Slack channel or user when committing or commenting

For example, if you'd like to create a pull request after a successful Push, include a command in your commit message:

```
17:56 $ git commit -m "$(cat <<-END
> this is my commit message
> but I can also add a command
>
> /pr --title 'my title' --base master
> END
> )"
```

# Before you get started

Connect and configure these integrations:

1. **GitHub**
2. **Slack** (optional)

The **GitHub** integration must be configured in order to use this skill. At least one repository must be selected. 
We recommend connecting the **Slack** integration.

# How to configure

To make the slash commands available in a repository, it must be enabled below. We recommend enabling this skill for
all repositories.

1. **Determine repository scope**

    ![Repository filter](docs/images/repo-filter.png)

    By default, this skill will be enabled for all repositories in all organizations you have connected.

    To restrict the organizations or specific repositories on which the skill will run, you can explicitly choose 
    organization(s) and repositories.
    

## How to use

1.  **Create a pull request from a commit message** 

    When you push a commit to a branch, and you're ready to raise a pull request, add a message to raise that pull request right in your
    commit message.  You can include this anywhere in the message:
    
    ```
    /pr --title 'any title surrounded by quotes' --base target-branch-ref
    ```
    
2.  **Add a comment to an open pull request**

    When you push to a branch with an open pull request, you can use commit messages to add additional 
    comments to that pull request. 

    ```
    /pr --base master --comment
    ```
    
    The rest of the commit message will be transcribed into the pull request comment body.

3.  **Label issues and pull requests froma a comment**

    When commenting on an issue, you can add labels to that issue by commenting directly in the issue:
    
    ```
    /label label1,label2
    ```
    
    You can also remove labels from an issue:
    
    ```
    /label --rm label1
    ```

4.  **Notify a Slack channel or user**

    This command works with our Slack integration. Add these slash commands in a commit message
    or in a comment. A link to the Commit, or to the Comment,
    will be sent to the Channel or User.  Slack channels must be prefixed by `#`, and Slack users
    must be prefixed by `@`.
    
    ```
    /cc #<slack-channel>
    /cc @<slack-user>
    ```

To create feature requests or bug reports, create an [issue in the repository for this skill](https://github.com/atomist-skills/git-chatops-skill/issues). 
See the [code](https://github.com/atomist-skills/git-chatops-skill) for the skill.

<!---atomist-skill-readme:end--->

---

Created by [Atomist][atomist].
Need Help?  [Join our Slack workspace][slack].

[atomist]: https://atomist.com/ (Atomist - How Teams Deliver Software)
[slack]: https://join.atomist.com/ (Atomist Community Slack) 
