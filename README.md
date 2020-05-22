# `atomist/git-chatops-skill`

<!---atomist-skill-readme:start--->

Run commands from git commit messages.

# What it's useful for

When you know that you want to trigger a command after Push, embed the command in a Commit message and just push.

For example, if you'd like to create a Pull Request after a successful Push, include a command in your Commit Message:

```
git commit -m "my fix message and then ... plus  atomist pr --title 'pr title' --base master" 
```

When configuring this skill, you can define the syntax that we'll use to parse these messages.

# Before you get started

Connect and configure these integrations:

1. **GitHub**

The **GitHub** integration must be configured in order to use this skill. At least one repository must be selected. We recommend connecting the **Slack** integration.

# How to configure

You can enable this skill without configuring any target versions.  In this mode, the skill will collect
data about your library versions, but will take no action.  Simply select the set of
repositories that should be scanned.

1. **Configure the keyword to recognize commands**

    Instead of the keyword `atomist`, use a different name to recognize the beginning of an instruction.

2. **Determine repository scope**

    ![Repository filter](docs/images/repo-filter.png)

    By default, this skill will be enabled for all repositories in all organizations you have connected.

    To restrict the organizations or specific repositories on which the skill will run, you can explicitly choose 
    organization(s) and repositories.
    

## How to use Update Clojure Tools Dependencies

1.  **Create PRs from a Commit message** 

    When you push a Commit to a branch, and you're ready to raise a PR, add a message to raise that PR right in your
    Commit message.  You can include this anywhere in the message:
    
    ```
    atomist pr --title 'any title surrounded by quotes' --base target-branch-ref
    ```
    
2.  **Add a Comment to an open PR**

    When you check in to an branch with an open Pull Request, you can use commit messages to add comments to that Pull
    Request. 

    ```
    atomist pr --base master --comment
    ```
    
    The rest of the commit message will be transcribed into the PR comment body.

3.  **Label Issues from Comments**

    When commenting on an issue, you can add labels to that issue by commenting directly in the Issue:
    
    ```
    atomist label label-name
    ```
    
    You can also remove labels from an Issue:
    
    ```
    atomist label rm label-name
    ```

To create feature requests or bug reports, create an [issue in the repository for this skill](https://github.com/atomist-skills/git-chatops-skill/issues). See the [code](https://github.com/atomist-skills/git-chatops-skill) for the skill.

<!---atomist-skill-readme:end--->

---

Created by [Atomist][atomist].
Need Help?  [Join our Slack workspace][slack].

[atomist]: https://atomist.com/ (Atomist - How Teams Deliver Software)
[slack]: https://join.atomist.com/ (Atomist Community Slack) 
