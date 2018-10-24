/*
Copyright 2018 Sam Pritchard

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
 */
package basalt.exceptions

/**
 * Represents an issue with the Basalt configuration file, most commonly thrown when a certain field is null.
 *
 * @author Sam Pritchard
 * @since 4.0.0
 * @property message The name of the field which caused this exception.
 */
class ConfigurationException(message: String): RuntimeException(message)