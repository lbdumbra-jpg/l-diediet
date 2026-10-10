const LddAuthorApi = Java.loadClass('com.lumbra.ldiediet.api.PackApi')
const LddAuthorIntegration = Java.loadClass('com.lumbra.ldiediet.api.IntegrationApi')
LddAuthorIntegration.registerCondition('test:kubejs_author', (player, args) => {
  if (!args.get('enabled').getAsBoolean()) return false
  LddAuthorApi.setNutrient(player, 'vegetables', 7)
  return true
})
ForgeEvents.onEvent('com.lumbra.ldiediet.api.DietEvents$NutrientPre', event => {
  if (event.getPlayer().getTags().contains('ldd_author_script') && event.getNutrient() === 'proteins') event.setValue(17)
})
console.info('LDD author API fixture registered')
