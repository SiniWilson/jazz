var lunrIndex, pagesIndex;

function initLunr() {
  var baseurl = document.location.origin;
  var rssURL = baseurl + '/index.xml';
  pagesIndex = [];
  // First retrieve the index file
  $.get(rssURL)
    .done(function (xml) {
      lunrIndex = new lunr.Index;
      lunrIndex.ref("link");
      lunrIndex.field('title', {
        boost: 15
      });
      lunrIndex.field('description', {
        boost: 10
      });
      $(xml).find('item').each(function (elementIndex) {
        var xmlNode = $(xml).find('item:eq(' + elementIndex + ')');
        var searchIndexElement = {
          "link": xmlNode.find('link').text(),
          "title": xmlNode.find('title').text(),
          "description": xmlNode.find('description').text()
        };
        pagesIndex.push(searchIndexElement);
        lunrIndex.add(searchIndexElement);
      });
      lunrIndex.pipeline.remove(lunrIndex.stemmer)
    })
    .fail(function (jqxhr, textStatus, error) {
      var err = textStatus + ", " + error;
      console.error("Error getting Hugo index file:", err);
    });
}

function search(query) {
  return lunrIndex.search(query).map(function (result) {
    return pagesIndex.filter(function (page) {
      return page.link === result.ref;
    })[0];
  });
}

initLunr();
$(document).ready(function () {
  var searchList = new autoComplete({
    selector: $("#search-by").get(0),
    source: function (term, response) {
      response(search(term));
    },
    renderItem: function (item, term) {
      var numContextWords = 2;
      var text = item.description.match(
        "(?:\\s?(?:[\\w]+)\\s?){0," + numContextWords + "}" +
        term + "(?:\\s?(?:[\\w]+)\\s?){0," + numContextWords + "}");
      item.context = text;
      return '<div class="autocomplete-suggestion" ' +
        'data-term="' + term + '" ' +
        'data-title="' + item.title + '" ' +
        'data-uri="' + item.link + '" ' +
        'data-context="' + item.description + '">' +
        '» ' + item.title +
        '<div class="context">' +
        (item.context || '') + '</div>' +
        '</div>';
    },
    onSelect: function (e, term, item) {
      location.href = item.getAttribute('data-uri');
    }
  });
});