# Confluence Wiki Plugin

This is a quickly-hacked together extension of an XMLRpc Confluence plugin used by Twitter. They use that to build and host their wiki, but it requires a self-hosted Atlassian server.

We use Atlassian Cloud, so this is an updated plugin that adapted the API to use the [Atlassian Cloud RESTful API](https://developer.atlassian.com/cloud/confluence/rest/).


## Wiki

This uses Pants to grab markdown files (`README.md` preferred) and builds html pages.
This auto-generated batch of html files is intended to be rebuilt on change and update the published wiki.

Moving links will result in build breaks until fixed, documentation requires code review, and it is easy to recognize code changes that should include documentation updates.


## Preview the HTML
`./pants markdown --open path/to/your:page`


## Adding a wiki page
Add a `page()` target to the directory with your `README.md` file
Example:

      page(
        name="page",
        source="README.md",
        links=[
          'src/python/fsqio/pants/buildgen/core',
          'src/python/fsqio/pants/buildgen:page',
        ],
        provides=[
          wiki_artifact(wiki=confluence,
            space='ENG',
            title='Pants Plugins',
          ),
        ],
      )

Pants uses the [Python Markdown module](http://pythonhosted.org/Markdown/) allows defining code snippets and links in the target so that your docs stay up to date!

[The Pants `page` docs are here](https://www.pantsbuild.org/page.html)


## Publishing
The wiki is meant to be published by CI. But the general workflow is like any other Pants task.

        ./pants confluence ::

Pants will look for every `page()` target defined in a BUILD file and process the markdown into HTML. Eventually, it will gather those HTML pages into the wiki structure and push to Confluence.

This requires an API key (not your Atlassian password!) ([directions here](https://confluence.atlassian.com/cloud/api-tokens-938839638.html)).

```
                         Processed README.md to /Users/mateo/dev/2/dist/markdown/html/src/python/fsqio/pants/README.html.
                         Processed README.md to /Users/mateo/dev/2/.pants.d/markdown/markdown/275102341/src/python/fsqio/pants/README.html
    00:11:37 00:02   [confluence]
    00:11:37 00:02     [confluence]Please enter API token (not password!) for https://${CONFLUENCE_URL} user: mateo@foursquare.com

    <mateo enters his API token>

                           Published Page(BuildFileAddress(src/python/fsqio/pants/buildgen/BUILD, page)) to https://${CONFLUENCE_URL}/wiki/spaces/ENG/pages/23003137/Buildgen+Plugins
                       Published Page(BuildFileAddress(src/python/fsqio/pants/BUILD, page)) to https://${CONFLUENCE_URL}/wiki/spaces/ENG/pages/23822374/Pants+Plugins
    00:17:10 05:35   [complete]
                   SUCCESS
```

## TODOs
1. Parent page and heirarchical structure is not quite working, all loose pages for now
1. Uploading images and attachments is untested
1. Github auto-renders markdown and converting the pure markdown links to Pants links breaks the Github version.
1. Should add "Edit" link to every page that redirects to the Github interface, edits in Confluence will be overwritten for auto-generated content.
1. Buildgen support with a built-in understanding of the page hierarchy
1. more?
