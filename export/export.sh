#
# Configure and export samples for building in the Android Studio
# Example:
#
# ./export.sh /home/wcs-android-sdk-1.0.1.2-release.aar
# cd output
# gradle build
#
# Here we provide .aar lib file for export
#

CURRENT_DIR=`pwd`

WORK_DIR=`dirname $CURRENT_DIR`

SDK_FILE=$1

if [ -z $SDK_FILE ]; then
    echo "Please provide full path to .aar file"
    exit 0
fi

if [ ! -f $SDK_FILE ]; then
    echo "Passed SDK .aar file does not exist"
    exit 0
fi

SDK_FILE_NAME="${SDK_FILE##*/}"
SDK_FILE_NAME_WO_EXT=`echo $SDK_FILE_NAME | sed s/\.aar//g`

EXPORT_DIR=$WORK_DIR/export
OUTPUT_DIR=$EXPORT_DIR/output

function export_samples {
    
    echo "export_samples"
    
    if [ -d "$OUTPUT_DIR" ]; then
	rm -Rf $OUTPUT_DIR	
    fi
    
    mkdir $OUTPUT_DIR  
    
    list=`cat $EXPORT_DIR/export_list`
    for sample in $list
    do
	echo "Export sample: $sample"
	export_sample $sample
    done
    
    export_sdk_lib
    export_build_configs
    
}

function export_sample {
    SAMPLE_DIR_NAME=$1    
    echo "export_sample SAMPLE_DIR_NAME: $SAMPLE_DIR_NAME"
    cp -r $WORK_DIR/$SAMPLE_DIR_NAME $OUTPUT_DIR
    DEST_DIR=$OUTPUT_DIR/$SAMPLE_DIR_NAME
    echo DEST_DIR: $DEST_DIR
    remove_dependency_from_build_file $DEST_DIR/build.gradle
    
}

function remove_dependency_from_build_file {
    SAMPLE_GRADLE=$1
    echo "remove_dependency_from_build_file SAMPLE_GRADLE: $SAMPLE_GRADLE"
    sed -i.bak '/:fp_wcs_api/d' $SAMPLE_GRADLE
    rm -f $SAMPLE_GRADLE.bak
}

function export_sdk_lib {
    echo "export_sdk_lib"
    SDK_DIR=$OUTPUT_DIR/libs
    mkdir $SDK_DIR
    cp $SDK_FILE $SDK_DIR
}

function export_build_configs {

    echo "export_build_configs"
    
    #build.gradle
    cp $EXPORT_DIR/build.gradle $OUTPUT_DIR
    #replace lib filename
    sed -i.bak s/fp_wcs_api-release/$SDK_FILE_NAME_WO_EXT/g $OUTPUT_DIR/build.gradle   
    rm -f $OUTPUT_DIR/build.gradle.bak
    
    #gradle.properties
    cp $WORK_DIR/gradle.properties $OUTPUT_DIR        
    
    #settings.gradle
    cp $WORK_DIR/settings.gradle $OUTPUT_DIR
    #remove fp_wcs_api from list
    sed -i.bak s/\':fp_wcs_api\',//g $OUTPUT_DIR/settings.gradle   
    rm -f $OUTPUT_DIR/settings.gradle.bak
    
    #local.properties    
    echo "sdk.dir=/opt/android-sdk-linux" > $OUTPUT_DIR/local.properties
    echo "ndk.dir=/opt/android-ndk-r12b" >> $OUTPUT_DIR/local.properties
    
}

export_samples