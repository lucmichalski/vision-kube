export default Ember.Route.extend({
  setupController: function(controller, model) {
    controller.set('pageTitle', 'Hosts');
    controller.set('model', model);
  },

  model: function() {
    this.store.find('host');
  }
});