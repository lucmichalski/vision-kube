import DS from 'ember-data';
export default DS.RESTAdapter.extend({
  namespace: 'v2',
  host: 'http://localhost:8183',

  updateRecord: function(store, type, snapshot) {
    var data = {};
    var serializer = store.serializerFor(type.typeKey);

    serializer.serializeIntoHash(data, type, snapshot);

    var id = snapshot.id;

    return this.ajax(this.buildURL(type.typeKey, null, snapshot), "POST", { data: data });
  }
});
