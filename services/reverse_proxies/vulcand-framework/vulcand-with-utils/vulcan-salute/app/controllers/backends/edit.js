export default Ember.Controller.extend({
  actions: {
    submit: function() {
      var backend = this.model;
      console.log(backend);
      backend.save();
    }
  }
})