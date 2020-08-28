# `atomist/github-slash-commands-skill`

<!---atomist-skill-readme:start--->

Run slash commands from commit messages and comments.

# What it's useful for

When you know that you want to trigger a command when committing code or commenting on a pull request or issue, just include the slash command.

-   Create a pull request directly from a commit message, no separate action needed to create the PR
-   Comment on an open pull request from a commit message
-   Add labels to an issue or pull request when commenting
-   Notify a Slack channel or user when committing or commenting

For example, if you'd like to create a pull request after a successful Push, include a command inside of your commit message:

```
$ git commit -m "$(cat <<-END
> this is my commit message
> but I can also add a command
>
> /pr --title 'my title' --base master --draft
> END
> )"
```

When this commit is pushed, the skill will create a pull request for this branch. In this example, the pull request
is created in draft mode. This indicates to users that the pull request is not yet ready for review.

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

### Add commands to Commit messages

1.  **Create a pull request from a commit message**

    When you push a commit to a branch, and you're ready to raise a pull request, add a message to raise that pull request right in your
    commit message. You can include this anywhere in the message:

    ```
    /pr --title 'any title surrounded by quotes' --base target-branch-ref --draft
    ```

    This is great when you are committing a new branch and you know that you want an open pull request. The new
    ability to place the pull request in draft mode can be useful, but this is optional.

2.  **Notify a User or Channel in Slack**

    Highlight this Commit for a User or a Slack channel, by mentioning them in the body of the commit message.  
    This command requires that the Slack integration is enabled for the team.

    ```
    Fixes Issue X
    /cc #channel
    ```

    Use the `@user` syntax to notify individual users.

    ```
    Fixes Issue Y
    /cc @alyssa
    ```

3.  **Create an Issue**

    ```
    Adding Feature X
    /issue create --title "TODO: I need some help documenting this" --assignee alyssa --assignee john --label documentation
    ```

4.  **Close a Pull Request**

    When committing to a branch with an open pull request, you can close any open pull requests associated with this branch:

    ```
    /pr close
    ```

    This can be useful when you've realized that the branch needs more work.  
    We are planning on adding a `/pr draft` to take the pull request back to draft mode, but we can't find the api!

5.  **Mark a draft Pull Request as Ready for review**

    Mark an open PR on this branch as ready for review with:

    ```
    /pr ready
    ```

    This is convenient when you've got a draft pull request open
    and you're making one more commit before marking it as being ready for review.

All of the above command can be combined. So a Commit message could create a pull request, and notify
Slack users in the same commit message.

```
Adding feature X

This is a backwards compatible change to the segments api
/pr --title 'feature X' --base main --draft --label api --label segment
/cc #segment-team
```

### Add commands to Issue or Pull Request Comments

We can also add commands to the comments of any issue, or pull request.

1.  **Add or remove labels**

    When commenting on an issue, or on a pull request, you can add labels by adding commands to the comment:

    ```
    /label label1,label2
    ```

    You can also remove labels:

    ```
    /label --rm label1
    ```

2.  **Make a draft Pull Request ready**

    Mark the current pull request as ready for review by including the command:

    ```
    /pr ready
    ```

3.  **Notify a Slack channel or user**

    Similar to above, you can highlight Issues for users or channels. This only works if our Slack integration
    has been enabled.

    ```
    /cc #<slack-channel>
    /cc @<slack-user>
    ```

To create feature requests or bug reports, create an [issue in the repository for this skill](https://github.com/atomist-skills/git-chatops-skill/issues).
See the [code](https://github.com/atomist-skills/git-chatops-skill) for the skill.

<!---atomist-skill-readme:end--->

---

Created by [Atomist][atomist].
Need Help? [Join our Slack workspace][slack].

[atomist]: https://atomist.com/ "Atomist - How Teams Deliver Software"
[slack]: https://join.atomist.com/ "Atomist Community Slack"
