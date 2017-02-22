// Lefalet shortcuts for common tile providers - is it worth adding such 1.5kb to Leaflet core?

L.TileLayer.Common = L.TileLayer.extend({
	initialize: function (options) {
		L.TileLayer.prototype.initialize.call(this, this.url, options);
	}
});

(function () {

	var osmAttr = '&copy; <a href="http://openstreetmap.org">OpenStreetMap</a> contributors, <a href="http://creativecommons.org/licenses/by-sa/2.0/">CC-BY-SA</a>';

	L.TileLayer.CloudMade = L.TileLayer.Common.extend({
		url: 'http://{s}.tile.cloudmade.com/{key}/{styleId}/256/{z}/{x}/{y}.png',
		options: {
			attribution: 'Map data ' + osmAttr + ', Imagery &copy; <a href="http://cloudmade.com">CloudMade</a>',
			styleId: 997
		}
	});

	L.TileLayer.OpenStreetMap = L.TileLayer.Common.extend({
		url: 'http://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png',
		options: {attribution: osmAttr}
	});

	L.TileLayer.OpenCycleMap = L.TileLayer.Common.extend({
		url: 'http://{s}.tile.opencyclemap.org/cycle/{z}/{x}/{y}.png',
		options: {
			attribution: '&copy; OpenCycleMap, ' + 'Map data ' + osmAttr
		}
	});

	L.TileLayer.MapBox = L.TileLayer.Common.extend({
		url: 'http://{s}.tiles.mapbox.com/v3/{user}.{map}/{z}/{x}/{y}.png'
	});

}());
