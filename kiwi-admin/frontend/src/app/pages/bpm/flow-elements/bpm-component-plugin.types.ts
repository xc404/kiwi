export interface BpmComponentBundleManifest {
  schemaVersion?: string;
  name?: string;
  version?: string;
  summary?: string;
  description?: string;
  readme?: string;
  author?: string;
  publisher?: string;
  license?: string;
  kiwiMinVersion?: string;
  homepage?: string;
  repository?: string;
  components?: BpmComponentBundleComponentEntry[];
}

export interface BpmComponentBundleComponentEntry {
  key: string;
  name: string;
  group?: string;
  version?: string;
  description?: string;
  type?: string;
  parentKey?: string;
  requiredParentKeys?: string[];
}

export interface BpmComponentPluginComponentInfo {
  key: string;
  name: string;
  group?: string;
  componentId: string;
  description?: string;
  version?: string;
  source?: 'manifest' | 'scanned' | string;
}

export interface BpmComponentPluginDescriptor {
  fileName: string;
  bundle?: BpmComponentBundleManifest;
  components?: BpmComponentPluginComponentInfo[];
  warnings?: string[];
  fileSizeBytes?: number;
  sha256?: string;
}
