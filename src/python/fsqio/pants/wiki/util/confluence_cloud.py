# coding=utf-8
# Copyright 2018 Foursquare Labs Inc. All Rights Reserved.

from __future__ import absolute_import, division, print_function

import base64
import getpass
import json
import logging
import os

from pants.contrib.confluence.util.confluence_util import Confluence, ConfluenceError
import requests


log = logging.getLogger(__name__)


class ConfluenceCloud(Confluence):

  @staticmethod
  def login(confluence_url, user=None, api_entrypoint='confluence2'):
    """Prompts the user to log in to confluence, and returns a Confluence object.
    :param confluence_url: Base url of wiki, e.g. https://confluence.atlassian.com/
    :param user: Username
    :param api_entrypoint: 'confluence1' or None results in Confluence 3.x. The default
                           value is 'confluence2' which results in Confluence 4.x or 5.x
    :rtype: returns a connected Confluence instance
    raises ConfluenceError if login is unsuccessful.

    # NOTE(mateo): This api_entrypoint is called by the upstream plugin - but they use this variable to refer to both
    # "confluence1/2" but also the session or proxyserver used to pass network calls.
    """
    # Actually impossible for 'user' to be unset - they now seed the config with this user.
    user = user or getpass.getuser()
    token = getpass.getpass('Please enter API token (not password!) for {} user: {}'.format(confluence_url, user))
    encoded = base64.b64encode('{}:{}'.format(user, token))

    if api_entrypoint in (None, 'confluence1'):
      fmt = 'markdown'
    elif api_entrypoint == 'confluence2':
      # NOTE(mateo): Confluence is very hesitant about allowing the HTML macro backend. For right now, experimenting
      # with markdown. If this allows human posted pages to live next to programmatic ones that could be very nice
      # or also maybe ruin the whole experiment.
      fmt = 'xhtml'
    else:
      raise ConfluenceError('Do not recognize api_entrypoint: {}'.format(api_entrypoint))

    jira_session = requests.Session()
    auth_headers = ConfluenceCloud.rest_headers(encoded)
    rest_api_url = confluence_url + '/wiki/rest/api'
    try:
      session = jira_session.get(rest_api_url, headers=auth_headers)
    except Exception as e:
      raise ConfluenceError('Failed to log in to {} as {}: {}'.format(rest_api_url, user, e))
    return ConfluenceCloud(jira_session, rest_api_url, encoded, 'markdown')

  @staticmethod
  def default_get_params(space):
    return {
      'spaceKey': space,
      'expand': 'body.storage,version,ancestors',
    }.copy()

  @staticmethod
  def get_url(page):
    """Given a page reponse from Confluence API, construct and return the human-facing URL for the page."""
    try:
      return page['_links']['base'] + page['_links']['webui']
    except Exception:
      raise Exception('Unable to parse URL from:\n{}.'.format(page))

  @staticmethod
  def get_content_value(page):
    return page['body']['storage']['value'].strip()

  @staticmethod
  def construct_body(content):
    return {
      'storage': {
        'representation': 'storage',
        'value': content
      },
    }

  @staticmethod
  def rest_headers(encoded_token):
    headers = {
      'Authorization': 'Basic {}'.format(encoded_token),
      'Content-Type': 'application/json',
    }
    return headers.copy()

  def get_content(self, space, params=None):
    """Query the entire confluence server, filtering based on the params."""
    #
    # GET /wiki/rest/api/content
    # https://developer.atlassian.com/cloud/confluence/rest/#api-content-get
    #
    params = params or self.default_get_params(space)
    response = self._api_entrypoint.get(
      self._server_url + '/content',
      headers=self.rest_headers(self._session_token),
      params=params,
    )
    if not response.ok:
      # TODO make a ConfluenceRest error that takes a response. object.
      raise ConfluenceError(
        'Could not fetch content for {}\nReason: {}: {}'.format(space, response.status_code, response.reason))
    return response.json()['results']

  def getpage(self, space, page_title):
    """Get the HTTP response for a single page.

    Returns None if no page is found and raises ConfluenceError if results > 1.
    :returns: json response from Confluence API Get Content request.
    """
    # This just creates the proper filters to pass to self.get_content.
    get_params = self.default_get_params(space)
    get_params['title'] = page_title
    results = self.get_content(space, get_params)
    if not results:
      return None
    if len(results) == 1:
      return results[0]
    raise ConfluenceError(
      'Returned multiple results for this getpage query!):\n{}'.format(results)
    )

  def create_html_page(self, space, title, html, parent_page=None, **pageoptions):
    # Another nod at potentially maintaining the upstream API.

    # I took out the HTTP upload in favor of simple markdown. Confluence disables HTTP by default due to XSS concerns.
    # This is not really an issue for us since we trust people providing input. We can choose to enable as needed.
    return self.create(space, title, html, parent_page, **pageoptions)

  def create(self, space, title, content, parent_page=None, **pageoptions):
    """ Create a new confluence page with the given title and content.  Additional page options
    available in the xmlrpc api can be specified as kwargs.
    returns the created page or None if the page could not be stored.
    raises ConfluenceError if a parent page was specified but could not be found.
    """
    pagedef = dict(
      space={'key': space},
      status='current',
      type='page',
      title=title,
      body=self.construct_body(content),
      content=content,  # The upstream execute function uses this - I am not sure if keeping that API is even worth it.
    )
    pagedef.update(**pageoptions)

    # TODO(mateo): Parent Page is not working correctly and blocks shipping. Solve if proposal accepted.
    if parent_page:
      parent_page_obj = self.getpage(space, parent_page)
      if not parent_page_obj:
        raise ConfluenceError(
          'Cannot find the parent (\"{}\") of (\"{}\") on Confluence.'.format(parent_page, title)
        )
      pagedef['ancestors'] = parent_page_obj['id']

    if not self.getpage(space, title):
      return self.create_new_page(pagedef)
    return self.storepage(pagedef)

  def create_new_page(self, page):
    """Create a new Confluence page.

    This will fail if called on an ID with preexisting content. Use self.updatepage(page) to change an existing page.

    https://developer.atlassian.com/cloud/confluence/rest/#api-content-post
    POST /wiki/rest/api/content
    """
    response = self._api_entrypoint.post(
      self._server_url + '/content',
      headers=self.rest_headers(self._session_token),
      data=json.dumps(page),
    )
    if not response.ok:
      raise ConfluenceError(
        'Could not create new Confluence page: {}\nReason: {}: {}'.format(page, response.status_code, response.reason),
      )
    new_page = response.json()
    return new_page

  def storepage(self, page):
    # Just a nod to upstream API. They use storepage but Confluence is now using Update Content.
    return self.update_page(page)

  def update_page(self, page):
    """Updates an existing page.

    This will fail if the page doesn't exist or if the version is not incremented by 1.

    PUT /wiki/rest/api/content/{id}
    https://developer.atlassian.com/cloud/confluence/rest/#api-content-id-put
    """
    updated_page = page.copy()
    updated_page['version']['number'] = page['version']['number'] + 1
    resp = self._api_entrypoint.put(
      self._server_url + '/content/' + updated_page['id'],
      headers=self.rest_headers(self._session_token),
      data=json.dumps(updated_page)
    )
    if not resp.ok:
      raise ConfluenceError('Failed to store page: {}/n{} from {}'.format(resp.url, resp.status_code, resp.reason))
    return resp.json()

  def removepage(self, pagedef):
    """Deletes a page from confluence.

    https://developer.atlassian.com/cloud/confluence/rest/#api-content-id-delete
    DELETE /wiki/rest/api/content/{id}
    raises ConfluenceError if the page could not be removed.
    """

    space = pagedef['space']['key']
    title = pagedef['title']
    page = self.getpage(space, title)
    page_id = page['id']
    resp = self._api_entrypoint.delete(
      self._server_url + '/content/' + page_id, headers=self.rest_headers(self._session_token)
    )
    if not resp.ok:
      raise ConfluenceError('Failed to delete page: {}'.format(resp.raise_for_status()))

  def addattachment(self, page, filename):
    """Add an attachment to an existing page.

    PUT /wiki/rest/api/content/{id}/child/attachment
    https://developer.atlassian.com/cloud/confluence/rest/#api-content-id-child-attachment-put
    """
    if not os.path.isfile(filename):
      raise ConfluenceError('No file found: {}'.format(filename))
    space = page['space']['key']
    title = page['title']
    # Fetches the current page data from Confluence.
    page_instance = self.getpage(space, title)

    with open(filename, 'rb') as file_payload:
      resp = self._api_entrypoint.put(
        '{}/content/{}/child/attachment'.format(self._server_url, page_instance['id']),
        headers={
          'Authorization': self.rest_headers(self._session_token)['Authorization'],
          'X-Atlassian-Token': 'nocheck'
        },
        files={
            'file': (os.path.basename(filename), file_payload),
            'minorEdit': 'true',
            'comment': 'Posted by Pants!',
        },
      )
      if not resp.ok:
        raise ConfluenceError('Failed to add attachment: {}\nError: {}'.format(filename, resp.raise_for_status()))

  def logout(self):
    """Terminates the session and connection to the server.
    Upon completion, the invoking instance is no longer usable to communicate with confluence.
    """
    # NOTE(mateo): Uncalled so far.
    self._api_entrypoint.close()
