module malt {
    requires transitive jloda;
    requires transitive megan;

    exports malt.tools;
    exports malt;

    opens malt.resources.icons;
}