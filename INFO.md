Create PRs, Issues, and send Slack notifications directly from your commit
messages, and issue comments.

# What it's useful for

Triggering commands directly from commit messages, or issue comments, can be
very convenient. For example:

-   create a draft pull request directly from your commit message to the new
    branch.
-   Automatically add labels to an issue or pull request based on comments
-   Request specific users or channels in Slack to be notified about your change

For example, when you write your commit message, you can include a request to
create a draft PR:

```
$ git commit -m "$(cat <<-END
> This change adds feature X
>
> /pr --title 'my title' --base master --draft
> END
> )"
```

When the branch ref for this Commit is pushed, the skill will create this PR on
your behalf.

# Before you get started

Connect and configure these integrations:

1. **GitHub**
2. **Slack** (optional)

The **GitHub** integration must be configured in order to use this skill. At
least one repository must be selected. We recommend connecting the **Slack**
integration.
