export default Ember.Controller.extend({
  actions: {
    submit: function(){
      var backend = this.store.createRecord('backend', this.get('model'));
      backend.save();
    }
  }
});