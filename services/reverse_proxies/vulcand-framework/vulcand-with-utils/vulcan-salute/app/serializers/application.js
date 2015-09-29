import DS from 'ember-data';
export default DS.RESTSerializer.extend({
  primaryKey: 'Id',

  keyForAttribute(key){
    return key.classify();
  },

  extractFind(store, type, payload, id, requestType) {
    payload = {[store.modelFor(type).typeKey.classify()]: payload};

    return this._super(store, type, payload, id, requestType);
  },

  extract(store, type, payload, id, requestType) {
    console.log(store);
    console.log(type);
    console.log(payload);
    console.log(id);
    console.log(requestType);

    this._super(store, type, payload, id, requestType);
  }
});