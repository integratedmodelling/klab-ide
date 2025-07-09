//package org.integratedmodelling.klab.ide.settings;
//
//import org.integratedmodelling.common.configuration.Settings;
//import org.integratedmodelling.klab.api.configuration.Configuration;
//
//public class IDESettings extends Settings {
//
//  public static final String PRIMARY_DISTRIBUTION = "klab.modeler.distribution.primary";
//  public static final String START_SERVICES_ON_STARTUP = "klab.modeler.services.start";
//
//  private Setting<String> primaryDistribution = new Setting<>(PRIMARY_DISTRIBUTION, "source");
//  private Setting<Boolean> startServicesOnStartup =
//      new Setting<>(START_SERVICES_ON_STARTUP, Boolean.FALSE);
//
//  public IDESettings() {
//    super(Configuration.INSTANCE.getFile("modeler.toml"));
//  }
//
//  public Setting<String> getPrimaryDistribution() {
//    return primaryDistribution;
//  }
//
//  public void setPrimaryDistribution(Setting<String> primaryDistribution) {
//    this.primaryDistribution = primaryDistribution;
//  }
//
//  public Setting<Boolean> getStartServicesOnStartup() {
//    return startServicesOnStartup;
//  }
//
//  public void setStartServicesOnStartup(Setting<Boolean> startServicesOnStartup) {
//    this.startServicesOnStartup = startServicesOnStartup;
//  }
//}
