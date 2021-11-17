# permalink-generator
> Permalink Generator will generate permalink for your displayable contents

## Installation

Please read the dedicated tutorial on https://academy.jahia.com/training-kb/tutorials/administrators/installing-a-module and select the Permalink Generator module from the store.

## Usage

The idea of this module is to try to add a vanity every time that property `jcr:title` is set:

```drools
rule "Create permanent URL on jcr:title set"
 when
         A property jcr:title has been set on a node
         - not in operation import
    then
        Create permanent URL for node node and language property.getLanguage()
end
```
Here is the way it works

1. It check if the module is enabled for your site
2. It check if the related node is a displayable node  (for node type with a dedicated content template, or for pages)
3. It check if current language is the default one. If not, then prefix the vanity with the language (for instance `/fr` for french)
4. Get all parent nodes of type `jmix:navMenuItem` (could be a page or a menu label), and get the title of this parent node.
5. It generates a SEO-friendly URLs using parent titles
6. It adds a new default vanity URL on the node


This module uses the Slugigy https://github.com/slugify/slugify library to create a SEO friendly name.

### Example of generated URL

Here is an example of page path:

`Home` / `Page 1` / `Page 2` / `Page 3`

When editing the page 3 title, the module will generate such a URL

`/page-1/page-2/page-3` or `/fr/page-1/page-2/page-3` if `fr` is not the default language. 



## Limitation

As the permanent link is based on the parent pages name, if you rename the title of a parent page, then the existing vanity for all the sub-nodes won't be updated.
