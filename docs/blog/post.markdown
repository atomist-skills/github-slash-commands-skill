This post is actually not about how to write a better commit message. It's about
writing commit messages that can trigger commands to run.

The idea for this feature came to us when GitHub introduced draft pull requests.
It was now possible to create pull requests that were not reviewable or
mergeable. This makes it feasible to begin socializing a change while it's still
in progress. We wondered whether it might be convenient to use a branch commit
to tell GitHub to create the draft pull request right then and there.

It turns out that we had several skills that were already parsing commit
messages (at Atomist, we call our units of automation "skills"). For example,
the [PR Commit Check skill][pcc-skill] automatically comments on non-compliant
commit messages, and the [Keep a Changelog skill][kac-skill]) uses commit
messages to maintain a change log. So it really wasn't a stretch to go that
extra step and start parsing commands out of these commit messages. Here's an
example that shows pull request creation:

```
$ git commit \
    -m "Feature(scope): switch to peer api" \
    -m "Migrating from client to peer api" \
    -m "/pr --title \"Peer Migration\" --base main --label api --draft"
```

(I only recently learned that you can use the `-m` flag multiple times in one
invocation of git commit. I'm afraid I may be overly enthusiastic about this
right now. Slash commands must be the first thing that occurs on a line but they
can occur anywhere in the commit message.)

The first two lines of the message are [conventional commits (literally)][cc].
The third line is a command to open a draft pull request. When this commit is
pushed, the GitHub application web hook fires, an event is delivered to the
[Github Slash Command skill][skill], and the pull request is opened. If you've
already installed the Atomist GitHub application, you can enable this command
for your commits with one click on our [skill management page][skill].

Once we have a pull request in draft mode, we can continue pushing commits.
Eventually, we'll probably want to open up this pull request for reviews. As a
GitHub slash command, this would like the following commit:

```
$ git commit \
    -m "Feature(scope): switch to peer api"
    -m "/pr ready"
```

The `/pr ready` command transitions the pull request from draft mode. If the
committer had wanted to assign a reviewer, or add a label, they can put that
into the message as well:

```
$ git commit \
    -m "Feature(scope): switch to peer api"
    -m "/pr ready --reviewer kipz --label staging"
```

We've also added commands to close pull requests (`/pr close`), create new
issues (`/issue create`), lock and unlock conversations (`/issue lock` and
`issue unlock`), and notify users or channels in Slack (`/cc`). These commands
can also be combined. For example, here's a commit message that will create a
Slack notification, a new GitHub Issue, and will also make the current pull
request as being ready for review:

```
Fix(api): switch to peer api

Migrating from client to peer api
/cc #api-team
/issue create --title "New Peer api documentation" --assignee alyssa --label documentation --label api
/pr ready
```

After satisfying ourselves that adding commands was straight forward, we started
looking for other places where we might be wasting our time typing command-less
messages.

### Issue and Pull Request Comments

GitHub issues and pull requests also raise events. Personally, I really like
doing label management directly within Issue comments. It has always bothered me
when I find myself in a GitHub repo that doesn't already have the label, and I
never have to worry about this when I write `/label mylabel` in a comment. This
helps me to standardize how how I use labels across different repos. The command
`/label bug, api, dev` will add 3 labels to the issue (and create the labels if
they don't already exist). I can also type `/label --rm dev` to remove a label
afterwards.

![github-slash-commands-skill-comment-1](https://blog.atomist.com/content/images/2020/09/github-slash-commands-skill-comment-1.png)

Some teams spend a lot of time in Slack. If you want to draw attention to an
issue, typing `/cc #docs` or `/cc @alyssa` to generate a Slack notification
might be just the thing. It is often while typing a comment that I realize that
I might be saying something interesting (finally) for a particular audience. If
you're already using our [Github Notifications skill][gn-skill], then you'll
have noticed something quite similar when GitHub logins are automatically
translated to @-mentions in Slack.

### Actions in Chat

This is an example of combining messages for both humans and bots in GitHub
issue comment.

![chatops](https://blog.atomist.com/content/images/2020/09/chatops.png)

This message is mostly meant for human readers. However, it also contains
sections intended for a bot user. Although technically speaking the bot parts
could be stripped out, there is some utility in leaving them behind. New members
can often learn useful operational details, just by reading the thread.

## User Attribution

For many operations, a GitHub app can operate as itself. However, users can also
authorize a GitHub application to do things _as_ _them_. This is the difference
between the two operations below. Both of these pull requests were created using
a `/pr` command in a git message.

![from-atomist](https://blog.atomist.com/content/images/2020/09/from-atomist.png)
![from-user](https://blog.atomist.com/content/images/2020/09/from-user.png)

In the first, the committer has not authorized the application to act on their
behalf. Therefore, the PR is created by the GitHub app installation. In the
second, the user has authorized the app to act as them and we are able to retain
user attribution. For operations like creating pull requests, keep track of the
author of the command can be important. To facilitate this, we use Commit
comments to send authorization links each time we run a command we run a pull
request command without user attribution.

There are also some commands that can _only_ run with a user authorization and
for those, going through the user-authorization flow one time is mandatory. One
example is `/pr ready`. If you issue this command without having first
authorized your user, the bot will also send an authorization link.

### Getting Started

It's easy to [get started][skill] if you want to try this out in your team.
Installing our GitHub app into at least one Organization is mandatory--we watch
for commands using the app's webhooks. The Slack integration is optional.

[pcc-skill]: https://go.atomist.com/catalog/skills/atomist/pr-commit-check-skill
[kac-skill]:
    https://go.atomist.com/catalog/skills/atomist/keep-a-changelog-skill
[gn-skill]:
    https://go.atomist.com/catalog/skills/atomist/github-notifications-skill
[skill]:
    https://go.atomist.com/catalog/skills/atomist/github-slash-commands-skill
[cc]: https://www.conventionalcommits.org/en/v1.0.0/
[how to write a good commit message]: https://chris.beams.io/posts/git-commit/
