export default DS.Model.extend({
  backendId: DS.attr('string'),
  route: DS.attr(),
  type: DS.attr('string'),
});